package com.aimodelhub.agent.service;

import com.aimodelhub.agent.entity.AgentAttachment;
import com.aimodelhub.agent.entity.AgentTask;
import com.aimodelhub.agent.enums.MessageRole;
import com.aimodelhub.agent.vo.AgentTaskResultVO;
import com.aimodelhub.agent.vo.ModelBriefVO;
import com.aimodelhub.common.exception.BusinessException;
import com.aimodelhub.predict.service.ModelInferenceService;
import com.aimodelhub.predict.vo.PredictResultVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 推理闭环：准备 → 用户确认 → 真实推理 → 结果解释。
 *
 * <h3>为什么「执行」必须由服务端状态驱动</h3>
 * 大模型很容易被诱导说出「已为你执行」，但它没有能力判断「用户到底点没点确认」。
 * 所以这里的分工是：
 * <ul>
 *   <li><b>准备</b>（{@link #prepare}）：可以做，产生一个 PENDING 任务，不碰模型；</li>
 *   <li><b>确认</b>：只能由携带用户 JWT 的 REST 接口触发，把状态改成 CONFIRMED；</li>
 *   <li><b>执行</b>（{@link #execute}）：只接受 CONFIRMED 状态，PENDING 一律拒绝。</li>
 * </ul>
 * 状态迁移用数据库条件更新保证幂等，重复点击确认不会推理两次。
 *
 * <h3>推理本身走哪里</h3>
 * 全部走 {@link ModelInferenceService}——与网页端 {@code POST /api/predict/**}
 * 完全同一条链路：模型上线校验、文件类型/大小校验、限流、MinIO 上传、
 * 推理记录落库，一个都不少。
 */
@Service
public class AgentInferenceService {

    private static final Logger log = LoggerFactory.getLogger(AgentInferenceService.class);

    /** 文本类模型允许的最大输入长度 */
    private static final int MAX_TEXT_LENGTH = 2000;

    private static final String EXPLAIN_PROMPT = """
            你是 AI 模型平台的结果解读助手。用户刚用平台上的模型跑了一次推理，
            你需要用通俗中文解释这次结果的业务含义。

            要求：
            - 只依据给出的结果数据，不要编造未出现的指标或类目。
            - 说明「这个结论意味着什么」，以及「置信度高低该怎么理解」。
            - 如果置信度偏低（<0.6）或候选类别之间差距很小，要明确提示结果可能不可靠。
            - 医学影像类结果必须补充一句：仅供科研与教学参考，不能作为诊断依据。
            - 3~5 句话，不要用列表，不要重复贴原始数字表格。
            """;

    private final AgentModelQueryService queryService;
    private final AgentAttachmentService attachmentService;
    private final AgentTaskService taskService;
    private final AgentSessionService sessionService;
    private final ModelInferenceService inferenceService;
    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final ObjectMapper objectMapper;

    public AgentInferenceService(AgentModelQueryService queryService,
                                 AgentAttachmentService attachmentService,
                                 AgentTaskService taskService,
                                 AgentSessionService sessionService,
                                 ModelInferenceService inferenceService,
                                 ChatClient chatClient,
                                 ChatMemory chatMemory,
                                 ObjectMapper objectMapper) {
        this.queryService = queryService;
        this.attachmentService = attachmentService;
        this.taskService = taskService;
        this.sessionService = sessionService;
        this.inferenceService = inferenceService;
        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
        this.objectMapper = objectMapper;
    }

    // ================================================================
    // 准备（不执行）
    // ================================================================

    /**
     * 校验输入并创建待确认任务。此方法<b>不会</b>调用模型服务。
     *
     * @param text         文本类模型的输入
     * @param features     结构化模型的输入
     * @param attachmentId 图像类模型先上传拿到的附件 id
     */
    public AgentTask prepare(Long userId, Long sessionId, Long modelId,
                             String text, Map<String, Object> features, Long attachmentId) {
        ModelBriefVO model = queryService.detail(modelId);
        String inputType = model.getInputType();
        if (inputType == null) {
            throw new BusinessException("模型「" + model.getName()
                    + "」缺少输入规范，暂时无法通过对话执行推理，请到在线体验页手动调用");
        }

        String summary;
        Map<String, Object> payload = new LinkedHashMap<>();
        Long usedAttachmentId = null;

        switch (inputType) {
            case "IMAGE" -> {
                if (attachmentId == null) {
                    throw new BusinessException("模型「" + model.getName() + "」需要图片输入，请先上传一张图片");
                }
                AgentAttachment attachment = attachmentService.require(userId, attachmentId);
                if (attachment.getContentType() == null || !attachment.getContentType().startsWith("image/")) {
                    throw new BusinessException("该附件不是图片，请重新上传");
                }
                summary = attachment.getOriginalFilename()
                        + "（" + Math.max(1, attachment.getFileSize() / 1024) + " KB）";
                usedAttachmentId = attachment.getId();
                payload.put("attachmentId", attachment.getId());
            }
            case "TEXT" -> {
                if (text == null || text.isBlank()) {
                    throw new BusinessException("模型「" + model.getName() + "」需要文本输入，请提供要分析的文本");
                }
                if (text.length() > MAX_TEXT_LENGTH) {
                    throw new BusinessException("文本过长（上限 " + MAX_TEXT_LENGTH + " 字符），请截断后重试");
                }
                summary = text.length() <= 50 ? text : text.substring(0, 50) + "…";
                payload.put("text", text);
            }
            case "FEATURES" -> {
                List<String> required = model.getRequiredFields() == null ? List.of() : model.getRequiredFields();
                Map<String, Object> cleaned = validateFeatures(required, features);
                summary = cleaned.entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("");
                if (summary.length() > 120) {
                    summary = summary.substring(0, 120) + "…";
                }
                payload.put("features", cleaned);
            }
            default -> throw new BusinessException("暂不支持的输入类型：" + inputType);
        }

        return taskService.create(userId, sessionId, modelId, model.getName(),
                inputType, summary, toJson(payload), usedAttachmentId);
    }

    /**
     * 校验结构化输入：必填字段齐全 + 必须是数字 + 不允许把非数字塞进数值模型。
     * <p>
     * 这是「大模型不能瞎编参数」的最后一道闸门——即使模型把
     * {@code sepal_length} 写成 {@code sepal-length}，也会在这里被拦下。
     */
    private Map<String, Object> validateFeatures(List<String> required, Map<String, Object> features) {
        if (features == null || features.isEmpty()) {
            throw new BusinessException("该模型需要结构化数值输入，缺少 fields：" + String.join(", ", required));
        }
        Map<String, Object> cleaned = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        for (String field : required) {
            Object value = features.get(field);
            if (value == null) {
                // 容错：允许大小写不一致（Open / open），但字段名必须真实存在
                value = features.entrySet().stream()
                        .filter(e -> e.getKey().equalsIgnoreCase(field))
                        .map(Map.Entry::getValue)
                        .findFirst()
                        .orElse(null);
            }
            if (value == null) {
                missing.add(field);
                continue;
            }
            if (value instanceof Number) {
                cleaned.put(field, value);
            } else {
                try {
                    cleaned.put(field, Double.parseDouble(String.valueOf(value)));
                } catch (NumberFormatException e) {
                    throw new BusinessException("字段 " + field + " 必须是数字，当前值：" + value);
                }
            }
        }
        if (!missing.isEmpty()) {
            throw new BusinessException("缺少必填字段：" + String.join(", ", missing));
        }
        return cleaned;
    }

    // ================================================================
    // 确认并执行（REST，用户点击确认）
    // ================================================================

    public AgentTaskResultVO confirmAndExecute(Long userId, Long taskId) {
        AgentTask task = taskService.requireOwned(userId, taskId);

        if (AgentTask.STATUS_SUCCESS.equals(task.getTaskStatus())) {
            throw new BusinessException("该任务已执行完成，无需重复执行");
        }
        if (AgentTask.STATUS_RUNNING.equals(task.getTaskStatus())) {
            throw new BusinessException("该任务正在执行中，请稍候");
        }
        if (!AgentTask.STATUS_PENDING.equals(task.getTaskStatus())) {
            throw new BusinessException("任务当前状态为 " + task.getTaskStatus() + "，无法执行");
        }

        taskService.confirm(userId, task);
        return execute(userId, task);
    }

    /**
     * 取任务并校验它<b>是否可以被执行</b>。
     * <p>
     * 这是工具侧的守门人：LLM 能拿到 taskId，但拿不到「用户已确认」这个事实。
     * 只有 REST 确认接口把状态改成 CONFIRMED 后，这里才会放行。
     */
    public AgentTask requireTaskForExecution(Long userId, Long taskId) {
        AgentTask task = taskService.requireOwned(userId, taskId);
        return switch (task.getTaskStatus()) {
            case AgentTask.STATUS_CONFIRMED, AgentTask.STATUS_SUCCESS -> task;
            case AgentTask.STATUS_PENDING -> throw new BusinessException(
                    "任务尚未经用户确认，不能执行。请先请用户点击「确认执行」按钮。");
            case AgentTask.STATUS_RUNNING -> throw new BusinessException("任务正在执行中，请稍候");
            default -> throw new BusinessException("任务状态为 " + task.getTaskStatus() + "，无法执行");
        };
    }

    // ================================================================
    // 执行（只接受 CONFIRMED 状态）
    // ================================================================

    /**
     * 真正调用模型服务。
     * <p>
     * 前置条件：任务状态必须是 CONFIRMED（由用户确认接口写入）。
     * 这里再用 {@code CONFIRMED → RUNNING} 的条件更新抢执行权，
     * 两个并发请求只有一个能拿到 {@code affected=1}。
     */
    public AgentTaskResultVO execute(Long userId, AgentTask task) {
        if (!taskService.claimForRun(userId, task.getId())) {
            AgentTask latest = taskService.findByIdAndUser(userId, task.getId());
            String status = latest == null ? "UNKNOWN" : latest.getTaskStatus();
            if (AgentTask.STATUS_SUCCESS.equals(status)) {
                return buildResultVO(latest, null, "该任务已执行完成，以下是上次的结果");
            }
            throw new BusinessException("任务已被执行或正在执行（当前状态：" + status + "）");
        }

        long start = System.currentTimeMillis();
        try {
            ModelInferenceService.InferenceOutcome outcome = runInference(userId, task);
            int duration = (int) (System.currentTimeMillis() - start);

            PredictResultVO result = outcome.result();
            String summary = result.topLabel == null ? "已完成"
                    : result.topLabel + (result.confidence == null ? "" : " " + String.format("%.4f", result.confidence));

            taskService.markSuccess(task.getId(), summary, toJson(result), outcome.recordId(), duration);
            // 同步内存对象状态，避免返回体里还是 CONFIRMED
            task.setTaskStatus(AgentTask.STATUS_SUCCESS);
            task.setRecordId(outcome.recordId());

            if (task.getAttachmentId() != null) {
                attachmentService.markUsed(userId, task.getAttachmentId());
            }

            String explanation = explain(task, result);
            appendToConversation(userId, task, result, explanation, outcome.recordId());

            log.info("Agent 推理完成: taskId={}, model={}, label={}, {}ms",
                    task.getId(), task.getModelName(), result.topLabel, duration);

            return buildResultVO(task, result, null, explanation, outcome.recordId());
        } catch (BusinessException e) {
            taskService.markFailed(task.getId(), e.getMessage(), (int) (System.currentTimeMillis() - start));
            throw e;
        } catch (Exception e) {
            int duration = (int) (System.currentTimeMillis() - start);
            taskService.markFailed(task.getId(), "推理服务异常：" + e.getMessage(), duration);
            log.error("Agent 推理失败: taskId={}", task.getId(), e);
            throw new BusinessException("推理执行失败，请稍后重试或到在线体验页手动调用");
        }
    }

    /** 按任务的输入类型分派到既有推理链路。 */
    private ModelInferenceService.InferenceOutcome runInference(Long userId, AgentTask task) {
        Map<String, Object> payload = parsePayload(task.getInputPayload());
        return switch (task.getInputType()) {
            case "IMAGE" -> {
                AgentAttachment attachment = attachmentService.require(userId, task.getAttachmentId());
                byte[] bytes = attachmentService.loadBytes(attachment);
                yield inferenceService.predictImage(userId, task.getModelId(),
                        new InMemoryMultipartFile("file", attachment.getOriginalFilename(),
                                attachment.getContentType(), bytes));
            }
            case "TEXT" -> inferenceService.predictText(userId, task.getModelId(),
                    String.valueOf(payload.getOrDefault("text", "")));
            case "FEATURES" -> inferenceService.predictTabular(userId, task.getModelId(), featuresOf(payload));
            default -> throw new BusinessException("不支持的输入类型：" + task.getInputType());
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> featuresOf(Map<String, Object> payload) {
        Object value = payload.get("features");
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    // ================================================================
    // 结果解释与对话回写
    // ================================================================

    /**
     * 让 DeepSeek 用自然语言解释结果。
     * <p>
     * 这是<b>唯一</b>允许模型「谈论结果」的地方：原始结果由服务端生成，
     * 模型只负责解读，不参与产生结论。模型不可用时退化为模板文案，不影响任务成功状态。
     */
    private String explain(AgentTask task, PredictResultVO result) {
        try {
            Map<String, Object> facts = new LinkedHashMap<>();
            facts.put("modelName", result.modelName != null ? result.modelName : task.getModelName());
            facts.put("topLabel", result.topLabel);
            facts.put("confidence", result.confidence);
            facts.put("candidates", result.candidates);
            facts.put("durationMs", result.duration);

            String answer = chatClient.prompt()
                    .system(EXPLAIN_PROMPT)
                    .user("推理结果数据（JSON）：\n" + toJson(facts))
                    .call()
                    .content();
            if (answer != null && !answer.isBlank()) {
                return answer.trim();
            }
        } catch (Exception e) {
            log.warn("结果解释生成失败，使用模板文案: {}", e.getMessage());
        }
        return templateExplanation(result);
    }

    private String templateExplanation(PredictResultVO result) {
        if (result.topLabel == null) {
            return "推理已完成，模型返回了结构化结果。";
        }
        String confidence = result.confidence == null ? "" :
                String.format("，置信度 %.2f%%", result.confidence * 100);
        return "模型判定结果为「" + result.topLabel + "」" + confidence
                + "。置信度越高说明模型越有把握；若数值偏低，建议换一张更清晰的输入或改用其他模型复核。";
    }

    /** 把「结果 + 解释」写回会话（MySQL）+ 上下文窗口（Redis），保证下一轮能继续追问。 */
    private void appendToConversation(Long userId, AgentTask task, PredictResultVO result,
                                      String explanation, Long recordId) {
        if (task.getSessionId() == null) {
            return;
        }
        StringBuilder text = new StringBuilder();
        text.append("**推理完成** · ").append(task.getModelName())
                .append("（任务 ").append(task.getTaskNo()).append("）\n\n");
        text.append("| 项 | 结果 |\n|---|---|\n");
        text.append("| 判定结果 | `").append(result.topLabel == null ? "-" : result.topLabel).append("` |\n");
        if (result.confidence != null) {
            text.append("| 置信度 | ").append(String.format("%.4f", result.confidence)).append(" |\n");
        }
        if (result.duration != null) {
            text.append("| 推理耗时 | ").append(result.duration).append(" ms |\n");
        }
        if (recordId != null) {
            text.append("| 记录 ID | ").append(recordId).append(" |\n");
        }
        text.append("\n").append(explanation);

        String content = text.toString();
        try {
            sessionService.append(task.getSessionId(), userId, MessageRole.ASSISTANT, content);
            // 同步进上下文窗口，用户接着问「为什么是这个结果」时模型能看到
            chatMemory.add(String.valueOf(task.getSessionId()), List.of(new AssistantMessage(content)));
        } catch (Exception e) {
            log.warn("推理结果回写会话失败: {}", e.getMessage());
        }
    }

    // ================================================================
    // 辅助
    // ================================================================

    public AgentTaskResultVO buildResultVO(AgentTask task, PredictResultVO result, String message) {
        return buildResultVO(task, result, message, null, task.getRecordId());
    }

    private AgentTaskResultVO buildResultVO(AgentTask task, PredictResultVO result,
                                            String message, String explanation, Long recordId) {
        AgentTaskResultVO vo = new AgentTaskResultVO();
        vo.setTaskId(task.getId());
        vo.setTaskNo(task.getTaskNo());
        vo.setStatus(task.getTaskStatus());
        vo.setModelId(task.getModelId());
        vo.setModelName(task.getModelName());
        vo.setRecordId(recordId);
        vo.setMessage(message);
        vo.setExplanation(explanation);
        if (result != null) {
            vo.setTopLabel(result.topLabel);
            vo.setConfidence(result.confidence);
            vo.setDurationMs(result.duration);
            if (result.candidates != null && !result.candidates.isEmpty()) {
                Map<String, Object> candidates = new LinkedHashMap<>();
                candidates.put("items", result.candidates);
                vo.setCandidates(candidates);
            }
        }
        return vo;
    }

    private Map<String, Object> parsePayload(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    /** 只读的 MultipartFile 实现：把磁盘上的字节包装成推理服务需要的入参。 */
    static final class InMemoryMultipartFile implements org.springframework.web.multipart.MultipartFile {

        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        InMemoryMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
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
        public java.io.InputStream getInputStream() {
            return new java.io.ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(java.io.File dest) throws java.io.IOException {
            java.nio.file.Files.write(dest.toPath(), content);
        }
    }
}
