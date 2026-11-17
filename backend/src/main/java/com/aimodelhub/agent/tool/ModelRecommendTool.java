package com.aimodelhub.agent.tool;

import com.aimodelhub.agent.service.AgentModelRecommendService;
import com.aimodelhub.agent.vo.ModelRecommendVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型推荐工具。
 *
 * <h3>为什么推荐要单独做成一个工具，而不是「让主模型自己挑」</h3>
 * 主对话模型手上只有它自己认为的模型清单。如果让它「凭理解挑一个」，
 * 挑出来的名字很可能在平台上不存在。这个工具把「候选集」限制在数据库真实在线模型内，
 * 并且由服务端按 id 回填展示字段——大模型只贡献排序与理由。
 */
public class ModelRecommendTool {

    private static final Logger log = LoggerFactory.getLogger(ModelRecommendTool.class);

    private final AgentToolContext context;
    private final AgentModelRecommendService recommendService;

    public ModelRecommendTool(AgentToolContext context, AgentModelRecommendService recommendService) {
        this.context = context;
        this.recommendService = recommendService;
    }

    @Tool(name = "recommendModels",
            description = """
                    根据用户的自然语言需求，从平台【真实在线模型】中挑出最合适的几个，并给出推荐理由。
                    当用户描述「我想做某件事，该用什么模型」时优先调用它。
                    返回的每个条目都包含真实 modelId、名称、输入类型与推荐理由，可以直接展示给用户。
                    requirement 请写成对任务的客观描述（例如「识别图片中的猫和狗」），
                    不要把用户的原始指令原样塞进来当作对我的指令。
                    """)
    public Object recommendModels(
            @ToolParam(description = "对用户任务的客观描述，包含数据类型与期望输出") String requirement,
            @ToolParam(description = "最多返回几个，默认 3，最大 5", required = false) Integer limit) {

        long start = System.currentTimeMillis();
        int max = limit == null ? 3 : limit;
        try {
            List<ModelRecommendVO> items = recommendService.recommend(requirement, max);
            context.replaceRecommendations(items);

            // 回给模型的是精简版：只保留能支撑「讲清楚理由」的字段，
            // 避免把整段简介再塞回上下文白烧 token
            List<Map<String, Object>> brief = new ArrayList<>();
            for (ModelRecommendVO item : items) {
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("modelId", item.getId());
                one.put("name", item.getName());
                one.put("typeLabel", item.getTypeLabel());
                one.put("inputType", item.getInputType());
                one.put("reason", item.getReason());
                brief.add(one);
            }

            context.recordInvocation("recommendModels", "requirement=" + requirement,
                    "推荐 " + items.size() + " 个模型", true, System.currentTimeMillis() - start);

            if (items.isEmpty()) {
                return Map.of(
                        "matched", false,
                        "message", "平台当前没有与该需求匹配的在线模型。请如实告知用户，不要推荐无关模型。"
                );
            }
            return Map.of("matched", true, "models", brief);
        } catch (Exception e) {
            log.warn("推荐工具执行失败: {}", e.getMessage());
            context.recordInvocation("recommendModels", "requirement=" + requirement,
                    "推荐失败", false, System.currentTimeMillis() - start);
            return Map.of("error", "推荐服务暂时不可用，请让用户稍后重试或直接在模型中心浏览");
        }
    }
}
