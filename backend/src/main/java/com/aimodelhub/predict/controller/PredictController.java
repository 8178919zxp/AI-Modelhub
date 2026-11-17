package com.aimodelhub.predict.controller;

import com.aimodelhub.common.api.Result;
import com.aimodelhub.predict.dto.CustomPredictDTO;
import com.aimodelhub.predict.dto.TabularPredictDTO;
import com.aimodelhub.predict.dto.TextPredictDTO;
import com.aimodelhub.predict.service.ModelInferenceService;
import com.aimodelhub.predict.vo.PredictResultVO;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 模型推理对外接口。
 *
 * <h3>职责说明（重构后）</h3>
 * 本类现在只做三件事：参数绑定、取当前用户、包装统一响应。
 * 全部业务规则（模型上线校验、文件校验、限流、MinIO、推理记录落库）
 * 已下沉到 {@link ModelInferenceService}，与 Agent 的推理工具共用同一套实现。
 * <p>
 * 对外行为与重构前完全一致：路径、入参、响应结构、错误码均未变。
 * 限流也在 Service 里显式调用 {@code SlidingWindowRateLimiter}，额度口径为
 * predict:user:{id}、20 次/60 秒，目的是让 Agent 路径也受同一额度约束。
 * Controller 上不放切面——Agent 不经过 AOP 代理，注解对它不生效。
 */
@RestController
@RequestMapping("/api/predict")
public class PredictController {

    private final ModelInferenceService inferenceService;

    public PredictController(ModelInferenceService inferenceService) {
        this.inferenceService = inferenceService;
    }

    @PostMapping("/text")
    public Result<PredictResultVO> text(@Valid @RequestBody TextPredictDTO dto) {
        return Result.ok(inferenceService.predictText(getCurrentUserIdOrNull(), dto.modelId, dto.text).result());
    }

    @PostMapping("/tabular")
    public Result<PredictResultVO> tabular(@RequestBody TabularPredictDTO dto) {
        return Result.ok(inferenceService.predictTabular(getCurrentUserIdOrNull(), dto.modelId, dto.features).result());
    }

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<PredictResultVO> image(@RequestPart("file") MultipartFile file,
                                         @RequestParam Long modelId) {
        return Result.ok(inferenceService.predictImage(getCurrentUserIdOrNull(), modelId, file).result());
    }

    @PostMapping("/custom")
    public Result<PredictResultVO> custom(@RequestBody CustomPredictDTO dto) {
        return Result.ok(inferenceService.predictCustom(
                getCurrentUserIdOrNull(), dto.modelId, dto.text, dto.features).result());
    }

    /** 从 SecurityContext 取用户 id；JwtAuthFilter 把 principal 固定成 Long。 */
    private Long getCurrentUserIdOrNull() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof Long userId) {
                return userId;
            }
        } catch (Exception ignored) {
            // 未登录场景（理论上被 SecurityConfig 拦住）返回 null
        }
        return null;
    }
}
