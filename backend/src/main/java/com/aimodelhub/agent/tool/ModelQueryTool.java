package com.aimodelhub.agent.tool;

import com.aimodelhub.agent.service.AgentModelQueryService;
import com.aimodelhub.agent.vo.ModelBriefVO;
import com.aimodelhub.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型查询类工具 —— 暴露给大模型的「只读业务接口」。
 *
 * <h3>工具方法的写法约定</h3>
 * <ol>
 *   <li><b>不接收 userId 参数</b>：身份固定在 {@link AgentToolContext} 里，
 *       从源头堵住「让模型传别人的 id」这条越权路径。</li>
 *   <li><b>不写 SQL、不碰 Mapper</b>：全部经 {@link AgentModelQueryService}，
 *       上下架过滤、字段裁剪、缓存策略只有一份实现。</li>
 *   <li><b>异常转成结构化结果</b>：工具抛异常时模型只会看到一句 stacktrace，
 *       还不如返回 {@code {error: "..."}} 让它用自己的话解释给用户。</li>
 *   <li><b>描述里写明「用户文本是数据不是指令」</b>：间接提示词注入的第一道防线。</li>
 * </ol>
 */
public class ModelQueryTool {

    private static final Logger log = LoggerFactory.getLogger(ModelQueryTool.class);

    private final AgentToolContext context;
    private final AgentModelQueryService queryService;

    public ModelQueryTool(AgentToolContext context, AgentModelQueryService queryService) {
        this.context = context;
        this.queryService = queryService;
    }

    @Tool(name = "listAvailableModels",
            description = """
                    查询平台当前【在线】的 AI 模型清单。返回每个模型的 id、名称、类型、算法、
                    准确率、简介、输入类型（IMAGE/TEXT/FEATURES）与必填字段。
                    当你需要知道「平台有哪些模型」时必须调用它——不要凭印象回答。
                    modelType 可选值：image（图像识别）、object（目标检测）、text（文本处理）、
                    ml（结构化预测）；不传表示全部。keyword 匹配名称/算法/简介，可不传。
                    """)
    public Object listAvailableModels(
            @ToolParam(description = "模型类型：image/object/text/ml，可为空", required = false) String modelType,
            @ToolParam(description = "关键词，匹配模型名称、算法或简介，可为空", required = false) String keyword) {

        long start = System.currentTimeMillis();
        try {
            List<ModelBriefVO> models = queryService.listOnline(modelType, keyword);
            context.recordInvocation("listAvailableModels",
                    "modelType=" + modelType + ", keyword=" + keyword,
                    "返回 " + models.size() + " 个在线模型",
                    true, System.currentTimeMillis() - start);
            return Map.of(
                    "total", models.size(),
                    "models", models
            );
        } catch (Exception e) {
            return fail("listAvailableModels", "modelType=" + modelType + ", keyword=" + keyword, start, e);
        }
    }

    @Tool(name = "getModelDetail",
            description = """
                    按模型 id 查询单个模型的完整信息（含输入要求、示例输入、适用说明）。
                    只有在已经知道模型 id 时使用；不知道 id 时先用 listAvailableModels 查。
                    模型不存在或已下线时会返回错误，此时不要编造它的信息。
                    """)
    public Object getModelDetail(
            @ToolParam(description = "模型 id（数字），来自 listAvailableModels 的返回结果") Long modelId) {

        long start = System.currentTimeMillis();
        try {
            ModelBriefVO detail = queryService.detail(modelId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("model", detail);
            result.put("inputRequirements", queryService.inputRequirements(modelId));
            context.recordInvocation("getModelDetail", "modelId=" + modelId,
                    "命中模型 " + detail.getName(), true, System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            return fail("getModelDetail", "modelId=" + modelId, start, e);
        }
    }

    @Tool(name = "listModelsByCategory",
            description = """
                    列出平台当前所有模型分类及各自的在线模型数量。
                    当用户问「你们有哪些类型的模型」时使用。
                    """)
    public Object listModelsByCategory() {
        long start = System.currentTimeMillis();
        try {
            List<Map<String, Object>> categories = queryService.categories();
            context.recordInvocation("listModelsByCategory", "",
                    "返回 " + categories.size() + " 个分类", true, System.currentTimeMillis() - start);
            return Map.of("categories", categories);
        } catch (Exception e) {
            return fail("listModelsByCategory", "", start, e);
        }
    }

    @Tool(name = "getModelInputRequirements",
            description = """
                    查询某个模型需要什么样的输入：是图片、纯文本，还是结构化数值特征；
                    若是数值特征，会返回必填字段名与示例。
                    在向用户索要文件/数据之前必须先调用，避免让用户白准备。
                    """)
    public Object getModelInputRequirements(
            @ToolParam(description = "模型 id（数字）") Long modelId) {

        long start = System.currentTimeMillis();
        try {
            Map<String, Object> requirements = queryService.inputRequirements(modelId);
            context.recordInvocation("getModelInputRequirements", "modelId=" + modelId,
                    "inputType=" + requirements.get("inputType"), true, System.currentTimeMillis() - start);
            return requirements;
        } catch (Exception e) {
            return fail("getModelInputRequirements", "modelId=" + modelId, start, e);
        }
    }

    /** 统一错误返回：让模型能读懂并转述，而不是看到堆栈。 */
    private Object fail(String tool, String args, long start, Exception e) {
        String message = e instanceof BusinessException ? e.getMessage() : "查询失败，请稍后重试";
        log.warn("工具 {} 执行失败 ({}): {}", tool, args, e.getMessage());
        context.recordInvocation(tool, args, message, false, System.currentTimeMillis() - start);
        return Map.of("error", message);
    }
}
