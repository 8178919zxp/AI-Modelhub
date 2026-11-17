package com.aimodelhub.agent.tool;

import com.aimodelhub.agent.entity.AgentTask;
import com.aimodelhub.agent.service.AgentInferenceService;
import com.aimodelhub.agent.vo.AgentTaskResultVO;
import com.aimodelhub.common.exception.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 推理工具 —— Agent「真正干活」的入口。
 *
 * <h3>两个工具，一条硬边界</h3>
 * <ul>
 *   <li>{@code prepareInference}：校验输入、创建 <b>PENDING</b> 任务，<b>不调用模型</b>。
 *       返回的 taskId 会通过 SSE 的 {@code confirm_required} 事件送到前端，
 *       由用户点「确认执行」。</li>
 *   <li>{@code executeInference}：只接受 <b>CONFIRMED</b> 状态。
 *       未确认时返回明确拒绝，而不是「先跑了再说」。</li>
 * </ul>
 * 也就是说：<b>LLM 可以发起「准备」，但无法自己完成「确认」</b>——
 * 确认这一步只有带用户 JWT 的 REST 接口能做。这条边界不写在提示词里，
 * 而是写在校验逻辑里。
 */
public class ModelPredictTool {

    private static final Logger log = LoggerFactory.getLogger(ModelPredictTool.class);

    private final AgentToolContext context;
    private final AgentInferenceService inferenceService;
    private final ObjectMapper objectMapper;

    public ModelPredictTool(AgentToolContext context,
                            AgentInferenceService inferenceService,
                            ObjectMapper objectMapper) {
        this.context = context;
        this.inferenceService = inferenceService;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "prepareInference",
            description = """
                    为「使用某个模型执行推理」做准备：校验模型是否在线、输入是否符合要求，
                    并生成一个【待用户确认】的任务。**本工具不会真正调用模型**。
                    调用成功后必须把「模型、输入、将要执行什么」告诉用户，并请其点击确认。

                    入参说明：
                    - modelId：模型 id，必须来自 listAvailableModels / recommendModels 的真实返回
                    - attachmentId：图片类模型的附件 id（用户已通过上传接口拿到并告诉你的那个数字）
                    - text：文本类模型的输入文本
                    - featuresJson：结构化模型的数值字段，JSON 对象字符串，
                      例如 {"sepal_length":5.1,"sepal_width":3.5,"petal_length":1.4,"petal_width":0.2}
                    三选一：图片模型传 attachmentId，文本模型传 text，数值模型传 featuresJson。
                    """)
    public Object prepareInference(
            @ToolParam(description = "模型 id（数字）") Long modelId,
            @ToolParam(description = "图片附件 id，仅图片类模型需要", required = false) Long attachmentId,
            @ToolParam(description = "文本输入，仅文本类模型需要", required = false) String text,
            @ToolParam(description = "结构化数值输入（JSON 对象字符串），仅数值类模型需要", required = false) String featuresJson) {

        long start = System.currentTimeMillis();
        try {
            Map<String, Object> features = parseFeatures(featuresJson);
            AgentTask task = inferenceService.prepare(
                    context.getUserId(), context.getSessionId(), modelId, text, features, attachmentId);

            // 记到上下文里，编排层据此推送 confirm_required 事件
            context.setPendingTaskId(task.getId());

            context.recordInvocation("prepareInference", "modelId=" + modelId,
                    "已创建待确认任务 " + task.getTaskNo(), true, System.currentTimeMillis() - start);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("taskId", task.getId());
            result.put("taskNo", task.getTaskNo());
            result.put("modelName", task.getModelName());
            result.put("inputType", task.getInputType());
            result.put("inputSummary", task.getInputSummary());
            result.put("status", task.getTaskStatus());
            result.put("nextStep", "已生成待确认任务。请把模型与输入信息告诉用户，请其点击「确认执行」；"
                    + "在用户确认前不要调用 executeInference。");
            return result;
        } catch (BusinessException e) {
            context.recordInvocation("prepareInference", "modelId=" + modelId,
                    e.getMessage(), false, System.currentTimeMillis() - start);
            return Map.of("error", e.getMessage());
        } catch (Exception e) {
            log.error("准备推理任务失败: modelId={}", modelId, e);
            context.recordInvocation("prepareInference", "modelId=" + modelId,
                    "准备失败", false, System.currentTimeMillis() - start);
            return Map.of("error", "准备推理任务失败，请稍后重试");
        }
    }

    @Tool(name = "executeInference",
            description = """
                    执行一个【已被用户确认】的推理任务，返回真实推理结果。
                    只有当用户明确表示「已确认 / 可以执行」，且任务状态为 CONFIRMED 时才会真正执行；
                    若任务仍处于待确认状态，本工具会拒绝执行。
                    执行成功后请用自然语言解释结果含义，不要只贴数字。
                    """)
    public Object executeInference(
            @ToolParam(description = "prepareInference 返回的 taskId") Long taskId) {

        long start = System.currentTimeMillis();
        try {
            // 状态校验在 Service 内：PENDING 会直接拒绝
            AgentTask task = inferenceService.requireTaskForExecution(context.getUserId(), taskId);
            AgentTaskResultVO vo = inferenceService.execute(context.getUserId(), task);

            context.recordInvocation("executeInference", "taskId=" + taskId,
                    "执行成功：" + vo.getTopLabel(), true, System.currentTimeMillis() - start);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("taskId", vo.getTaskId());
            result.put("status", vo.getStatus());
            result.put("modelName", vo.getModelName());
            result.put("topLabel", vo.getTopLabel());
            result.put("confidence", vo.getConfidence());
            result.put("durationMs", vo.getDurationMs());
            result.put("recordId", vo.getRecordId());
            return result;
        } catch (BusinessException e) {
            context.recordInvocation("executeInference", "taskId=" + taskId,
                    e.getMessage(), false, System.currentTimeMillis() - start);
            return Map.of("error", e.getMessage());
        } catch (Exception e) {
            log.error("执行推理任务失败: taskId={}", taskId, e);
            context.recordInvocation("executeInference", "taskId=" + taskId,
                    "执行失败", false, System.currentTimeMillis() - start);
            return Map.of("error", "执行推理失败，请稍后重试");
        }
    }

    private Map<String, Object> parseFeatures(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new BusinessException("featuresJson 不是合法的 JSON 对象，请检查格式");
        }
    }
}
