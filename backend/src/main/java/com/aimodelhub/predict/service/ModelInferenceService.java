package com.aimodelhub.predict.service;

import com.aimodelhub.common.exception.BusinessException;
import com.aimodelhub.common.exception.RateLimitException;
import com.aimodelhub.model.entity.ModelInfo;
import com.aimodelhub.model.service.ModelService;
import com.aimodelhub.predict.vo.PredictResultVO;
import com.aimodelhub.ratelimit.SlidingWindowRateLimiter;
import com.aimodelhub.service.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 模型推理的唯一业务入口。
 *
 * <h3>为什么要从 Controller 里抽出来</h3>
 * 原先四条推理链路（text / tabular / image / custom）的逻辑都内联在
 * {@code PredictController} 里，包括：模型上线校验、model_api 校验、文件字节缓存、
 * MinIO 上传、网关调用、结果映射、推理记录落库、限流。
 * <p>
 * Agent 也需要执行推理。如果 Agent 直接调 {@link ModelGatewayService}，
 * 上述校验与落库全部会被绕过——于是就出现「网页推理有校验、Agent 推理没有」两套逻辑，
 * 这正是最典型的安全漏洞来源。
 * <p>
 * 因此把这段流程<b>原样下沉</b>到本类：
 * <pre>
 *   PredictController ─┐
 *                      ├─→ ModelInferenceService ─→ ModelGatewayService / MinIO / predict_record
 *   AgentInferenceService ┘
 * </pre>
 * 两条链路共用同一套规则，Controller 只保留参数绑定。
 *
 * <h3>限流为什么也放这里</h3>
 * 限流必须覆盖「网页推理」与「Agent 推理」两条路径。Agent 直接调本类方法、
 * 不经过任何 AOP 代理，所以挂在 Controller 上的切面永远管不到它。
 * 把限流显式写进本类后，两条路径共用同一个 20 次/60 秒额度，
 * Agent 不会变成绕过配额的旁路。
 */
@Service
public class ModelInferenceService {

    private static final Logger log = LoggerFactory.getLogger(ModelInferenceService.class);

    /** 配额口径：每用户每 60 秒 20 次（网页推理与 Agent 推理共用） */
    private static final int RATE_LIMIT_MAX = 20;
    private static final int RATE_LIMIT_WINDOW_SECONDS = 60;

    /** 推理结果 + 落库后的记录 id（Agent 需要把 task 与 predict_record 关联起来）。 */
    public record InferenceOutcome(PredictResultVO result, Long recordId) {
    }

    private final ModelGatewayService modelGatewayService;
    private final PredictRecordService predictRecordService;
    private final FileStorageService fileStorageService;
    private final ModelService modelService;
    private final SlidingWindowRateLimiter rateLimiter;

    public ModelInferenceService(ModelGatewayService modelGatewayService,
                                 PredictRecordService predictRecordService,
                                 FileStorageService fileStorageService,
                                 ModelService modelService,
                                 SlidingWindowRateLimiter rateLimiter) {
        this.modelGatewayService = modelGatewayService;
        this.predictRecordService = predictRecordService;
        this.fileStorageService = fileStorageService;
        this.modelService = modelService;
        this.rateLimiter = rateLimiter;
    }

    // ================================================================
    // 文本
    // ================================================================

    public InferenceOutcome predictText(Long userId, Long modelId, String text) {
        checkRateLimit(userId);
        ModelInfo model = requireOnlineModel(modelId);
        String modelKey = requireModelKey(model);
        if (text == null || text.isBlank()) {
            throw new BusinessException("text 不能为空");
        }

        long startTime = System.currentTimeMillis();
        // bert 继续走原有的专用接口；其余文本模型按 model_key 走 /predict/custom，
        // 这样在后台新增文本模型后无需改动代码即可生效。
        Map<String, Object> result = "bert".equals(modelKey)
                ? modelGatewayService.predictText(text, modelId)
                : modelGatewayService.predictCustom(modelKey, Map.of("text", text));
        int duration = (int) (System.currentTimeMillis() - startTime);

        PredictResultVO vo = PredictResultMapper.fromMap(result);
        vo.duration = duration;

        Long recordId = null;
        if (userId != null) {
            recordId = predictRecordService.saveRecord(userId, modelId, text, null, null,
                    vo.topLabel, vo.confidence, duration).getId();
        }
        return new InferenceOutcome(vo, recordId);
    }

    // ================================================================
    // 结构化数据
    // ================================================================

    public InferenceOutcome predictTabular(Long userId, Long modelId, Map<String, Object> features) {
        checkRateLimit(userId);
        ModelInfo model = requireOnlineModel(modelId);
        String modelKey = requireModelKey(model);
        if (features == null || features.isEmpty()) {
            throw new BusinessException("features 不能为空");
        }

        long startTime = System.currentTimeMillis();
        Map<String, Object> result;
        if ("xgboost".equals(modelKey)) {
            result = modelGatewayService.predictTabular(features, modelId);
        } else {
            Map<String, Object> payload = new HashMap<>();
            payload.put("features", features);
            result = modelGatewayService.predictCustom(modelKey, payload);
        }
        int duration = (int) (System.currentTimeMillis() - startTime);

        PredictResultVO vo = PredictResultMapper.fromMap(result);
        vo.duration = duration;

        Long recordId = null;
        if (userId != null) {
            recordId = predictRecordService.saveRecord(userId, modelId, features.toString(), null, null,
                    vo.topLabel, vo.confidence, duration).getId();
        }
        return new InferenceOutcome(vo, recordId);
    }

    // ================================================================
    // 自定义模型（天气 / 股票 / 中文情感）
    // ================================================================

    public InferenceOutcome predictCustom(Long userId, Long modelId, String text, Map<String, Object> features) {
        checkRateLimit(userId);
        ModelInfo model = requireOnlineModel(modelId);
        String modelKey = requireModelKey(model);
        if ((text == null || text.isBlank()) && (features == null || features.isEmpty())) {
            throw new BusinessException("text 与 features 至少提供一个");
        }

        log.info("Custom prediction: modelId={} modelKey={}", modelId, modelKey);

        Map<String, Object> payload = new HashMap<>();
        if (text != null) {
            payload.put("text", text);
        }
        if (features != null) {
            payload.put("features", features);
        }

        long startTime = System.currentTimeMillis();
        Map<String, Object> result = modelGatewayService.predictCustom(modelKey, payload);
        int duration = (int) (System.currentTimeMillis() - startTime);

        PredictResultVO vo = PredictResultMapper.fromMap(result);
        vo.duration = duration;

        Long recordId = null;
        if (userId != null) {
            recordId = predictRecordService.saveRecord(userId, modelId,
                    text != null ? text : String.valueOf(features),
                    null, null, vo.topLabel, vo.confidence, duration).getId();
        }
        return new InferenceOutcome(vo, recordId);
    }

    // ================================================================
    // 图像
    // ================================================================

    /**
     * 图像推理主流程：校验模型 → 读取并缓存文件 → 上传 MinIO → 调用推理服务 → 落库。
     * <p>
     * MinIO 不可用时<b>不阻断推理</b>，只是不保存图片链接（这是原有行为，此处原样保留）。
     */
    public InferenceOutcome predictImage(Long userId, Long modelId, MultipartFile file) {
        checkRateLimit(userId);
        ModelInfo model = requireOnlineModel(modelId);
        String modelKey = requireModelKey(model);
        log.info("Image prediction: modelId={} type={} modelKey={} (from model_api)",
                modelId, model.getModelType(), modelKey);

        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要识别的图片");
        }

        // 1. 先把文件字节读出来并缓存——原始 MultipartFile 的 InputStream 只能读一次。
        //    MinIO 上传与调用模型服务都要读文件内容。
        final byte[] fileBytes;
        final String originalContentType = file.getContentType();
        final String originalName = file.getOriginalFilename();
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            log.error("Failed to read uploaded file bytes", e);
            throw new BusinessException("读取上传文件失败");
        }

        MultipartFile cachedFile = new ByteArrayMultipartFile(
                file.getName(), originalName, originalContentType, fileBytes);

        // 2. 先上传到 MinIO
        String fileUrl = null;
        String originalFilename = originalName;
        try {
            FileStorageService.UploadResult uploadResult =
                    fileStorageService.upload(cachedFile, "predictions");
            fileUrl = uploadResult.publicUrl();
            originalFilename = uploadResult.originalFilename();
            log.info("Image uploaded to MinIO: {} → {}", originalFilename, fileUrl);
        } catch (IllegalArgumentException e) {
            // 文件类型/大小校验不通过：这是用户输入问题，必须阻断
            throw new BusinessException(e.getMessage());
        } catch (Exception e) {
            log.error("MinIO upload failed", e);
            // MinIO 不可用时继续执行——预测照常进行，只是不保存文件 URL
        }

        // 3. 调用模型服务执行预测
        long startTime = System.currentTimeMillis();
        Map<String, Object> result = modelGatewayService.predictImage(cachedFile, modelKey);
        int duration = (int) (System.currentTimeMillis() - startTime);

        PredictResultVO vo = PredictResultMapper.fromMap(result);
        vo.duration = duration;
        if ("u2net".equals(modelKey)) {
            vo.resultType = "image_processing";
            if (vo.imageBase64 == null && result.get("imageBase64") != null) {
                vo.imageBase64 = String.valueOf(result.get("imageBase64"));
            }
        }

        // 4. 保存带文件 URL 的预测记录
        Long recordId = null;
        if (userId != null) {
            recordId = predictRecordService.saveRecord(userId, modelId, null, originalFilename, fileUrl,
                    vo.topLabel, vo.confidence, duration).getId();
        }
        return new InferenceOutcome(vo, recordId);
    }

    // ================================================================
    // 公共校验
    // ================================================================

    /**
     * 限流：按用户维度计数（identifier = {@code predict:user:{userId}}，20 次 / 60 秒）。
     * <p>
     * 未登录用户走不到这里——{@code /api/predict/**} 与 {@code /api/agent/**}
     * 都要求认证，匿名请求在过滤器链就被拦了，所以没有 IP 维度的配额。
     * <p>
     * Redis 不可用时 {@link SlidingWindowRateLimiter#isAllowed} 内部已 fail-open。
     */
    private void checkRateLimit(Long userId) {
        if (userId == null) {
            return;
        }
        if (!rateLimiter.isAllowed("predict:user:" + userId, RATE_LIMIT_MAX, RATE_LIMIT_WINDOW_SECONDS)) {
            throw new RateLimitException("请求过于频繁，请稍后再试（每分钟最多 " + RATE_LIMIT_MAX + " 次）");
        }
    }

    /**
     * 加载模型，并在模型不可用于推理时立即失败。
     * 用 {@code Integer.valueOf(...).equals(...)} 比较，避免 status 为 NULL 时自动拆箱触发空指针。
     */
    public ModelInfo requireOnlineModel(Long modelId) {
        ModelInfo model = modelService.getModelById(modelId);
        if (!Integer.valueOf(ModelService.STATUS_ONLINE).equals(model.getStatus())) {
            throw new BusinessException("模型未上线，无法调用");
        }
        return model;
    }

    /**
     * {@code model_info.model_api} 存的是 model-service 的模型 key（如 resnet50），绝不是 URL。
     */
    public String requireModelKey(ModelInfo model) {
        String modelKey = model.getModelApi();
        if (modelKey == null || modelKey.isBlank()) {
            throw new BusinessException("模型 " + model.getId() + " 未配置有效的 model_api");
        }
        return modelKey;
    }

    /**
     * 基于已缓存字节的可重复读 MultipartFile。
     * <p>
     * 为什么需要它：Tomcat 的 MultipartFile 底层是临时文件/流，
     * 同一实例被 MinIO 上传消费一次后，再传给推理服务时可能已经读空。
     */
    static final class ByteArrayMultipartFile implements MultipartFile {

        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        ByteArrayMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(File dest) throws IOException {
            java.nio.file.Files.write(dest.toPath(), content);
        }
    }
}
