package com.aimodelhub.agent.tool;

import com.aimodelhub.agent.service.AgentKnowledgeService;
import com.aimodelhub.agent.vo.KnowledgeHitVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库检索工具。
 *
 * <h3>为什么知识库要有独立工具</h3>
 * 系统提示词里已经会「预检索」一次并注入资料，但用户可能追问到上一轮没检索的方向。
 * 给模型一个主动检索的入口，它就能自己补查，而不是硬答。
 * <p>
 * 工具返回的内容一律标注来源（文档标题 + 小节），
 * 便于模型在回答里说清「依据哪份文档」，也便于事后核对。
 */
public class KnowledgeTool {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeTool.class);

    private static final int MAX_CONTENT_CHARS = 600;

    private final AgentToolContext context;
    private final AgentKnowledgeService knowledgeService;

    public KnowledgeTool(AgentToolContext context, AgentKnowledgeService knowledgeService) {
        this.context = context;
        this.knowledgeService = knowledgeService;
    }

    @Tool(name = "searchKnowledgeBase",
            description = """
                    检索平台知识库（模型使用说明、输入输出规范、常见问题、平台操作手册）。
                    当用户询问「怎么用」「有什么限制」「为什么失败」「规范是什么」这类
                    需要文档依据的问题时调用。
                    注意：模型是否在线、有哪些模型、要填哪些字段，一律以模型查询工具返回为准，
                    知识库不覆盖这些事实。检索不到时请如实告知用户「知识库暂无相关资料」。
                    """)
    public Object searchKnowledgeBase(
            @ToolParam(description = "检索关键词或问题，尽量保留专有名词（模型名、字段名）") String query) {

        long start = System.currentTimeMillis();
        try {
            List<KnowledgeHitVO> hits = knowledgeService.retrieve(query, 5);
            context.recordInvocation("searchKnowledgeBase", "query=" + query,
                    hits.isEmpty() ? "无命中" : "命中 " + hits.size() + " 段资料",
                    true, System.currentTimeMillis() - start);

            if (hits.isEmpty()) {
                return Map.of(
                        "found", false,
                        "message", "知识库中没有与该问题匹配的资料。请如实告知用户，不要编造文档内容。"
                );
            }

            List<Map<String, Object>> items = new ArrayList<>();
            for (KnowledgeHitVO hit : hits) {
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("source", hit.getDocTitle());
                one.put("section", hit.getHeading());
                String content = hit.getContent();
                one.put("content", content.length() <= MAX_CONTENT_CHARS
                        ? content : content.substring(0, MAX_CONTENT_CHARS) + "…");
                items.add(one);
            }
            return Map.of("found", true, "documents", items);
        } catch (Exception e) {
            log.warn("知识库检索失败: {}", e.getMessage());
            context.recordInvocation("searchKnowledgeBase", "query=" + query,
                    "检索失败", false, System.currentTimeMillis() - start);
            return Map.of("error", "知识库检索暂时不可用，请稍后重试");
        }
    }
}
