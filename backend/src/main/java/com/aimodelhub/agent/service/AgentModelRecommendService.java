package com.aimodelhub.agent.service;

import com.aimodelhub.agent.vo.ModelBriefVO;
import com.aimodelhub.agent.vo.ModelRecommendVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能模型推荐。
 *
 * <h3>四步流程（顺序不能换）</h3>
 * <ol>
 *   <li><b>数据库筛选</b>：取出全部在线模型 + 输入规范。候选集来自 MySQL，不来自模型记忆。</li>
 *   <li><b>大模型语义分析</b>：把候选集（id / 名称 / 类型 / 简介 / 输入类型）交给 DeepSeek，
 *       要求它输出 JSON：给每个候选打分 + 写推荐理由。</li>
 *   <li><b>服务端回填</b>：按 id 回查真实数据，<b>不在候选集里的 id 直接丢弃</b>。
 *       模型返回的名称、状态一律不采信——它只能决定「排序和理由」。</li>
 *   <li><b>返回</b>：按分数排序、截断数量，交给上层渲染卡片或写进对话。</li>
 * </ol>
 *
 * <h3>为什么用 JSON 模式而不是让它自由发挥</h3>
 * 自由文本没法可靠地取出 modelId，只能靠正则猜；一旦猜错就把用户的请求导到别的模型上。
 * DeepSeek 支持 {@code response_format=json_object}，用它换结构稳定性是划算的。
 */
@Service
public class AgentModelRecommendService {

    private static final Logger log = LoggerFactory.getLogger(AgentModelRecommendService.class);

    /** 一次最多推荐几个，太多反而让用户难以选择 */
    private static final int DEFAULT_LIMIT = 3;

    private static final String SYSTEM_PROMPT = """
            你是 AI 模型平台的选型顾问。用户会用自然语言描述任务，你需要从「候选模型」中挑出最合适的。

            ## 严格规则
            1. 只能从给定的候选模型中选择，**禁止编造**候选集之外的模型。
            2. 只能使用候选集中给出的 id / 名称 / 类型 / 输入类型，不得改写或臆测。
            3. 如果候选集里没有能完成该任务的模型，返回空数组，并在 summary 里说明缺什么能力。
            4. 推荐理由要具体：说明「为什么这个模型适合这个任务」，而不是重复简介。
            5. 只输出 JSON，不要 Markdown 代码块，不要解释文字。

            ## 输出格式
            {"ranked":[{"modelId":1,"score":0.92,"reason":"一句话理由"}],"summary":"一句话总结"}
            score 取值 0~1，表示与用户任务的匹配度。
            """;

    private final ChatClient chatClient;
    private final AgentModelQueryService queryService;
    private final ObjectMapper objectMapper;

    public AgentModelRecommendService(ChatClient chatClient,
                                      AgentModelQueryService queryService,
                                      ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.queryService = queryService;
        this.objectMapper = objectMapper;
    }

    /**
     * 根据需求推荐模型。
     *
     * @param requirement 用户的自然语言需求
     * @param limit       最多返回几个
     * @return 推荐列表；候选集为空或无匹配时返回空列表（不强行推荐）
     */
    public List<ModelRecommendVO> recommend(String requirement, int limit) {
        int max = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, 5);
        List<ModelBriefVO> candidates = queryService.allOnline();
        if (candidates.isEmpty()) {
            return List.of();
        }

        Map<Long, ModelBriefVO> byId = new HashMap<>();
        candidates.forEach(c -> byId.put(c.getId(), c));

        List<ModelRecommendVO> ranked;
        try {
            ranked = askModel(requirement, candidates, byId, max);
        } catch (Exception e) {
            // 大模型不可用时退化为「按类型关键词打分」——仍然只返回真实模型，
            // 只是没有语义理由。宁可降级，也不要让推荐功能整体 500。
            log.warn("推荐调用大模型失败，降级为规则匹配: {}", e.getMessage());
            ranked = fallback(requirement, candidates);
        }

        if (ranked.isEmpty()) {
            log.info("推荐为空：需求「{}」在候选集 {} 个模型中无匹配", requirement, candidates.size());
        }
        return ranked.stream().limit(max).toList();
    }

    // ================================================================
    // 内部
    // ================================================================

    private List<ModelRecommendVO> askModel(String requirement,
                                            List<ModelBriefVO> candidates,
                                            Map<Long, ModelBriefVO> byId,
                                            int limit) throws Exception {
        String candidateJson = objectMapper.writeValueAsString(candidates);
        String userPrompt = """
                用户需求：
                %s

                候选模型（JSON 数组，字段含义：id=模型ID，name=名称，type=类型，typeLabel=中文类型，
                description=简介，inputType=输入类型(IMAGE/TEXT/FEATURES)，requiredFields=必填字段）：
                %s

                请最多返回 %d 个最合适的模型，按匹配度从高到低排序。
                """.formatted(requirement, candidateJson, limit);

        String raw = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                // 推荐场景要确定性：温度拉到最低，且强制 JSON 输出
                .options(OpenAiChatOptions.builder()
                        .temperature(0.1)
                        .maxTokens(1500)
                        .responseFormat(ResponseFormat.builder()
                                .type(ResponseFormat.Type.JSON_OBJECT)
                                .build())
                        .build())
                .call()
                .content();

        return parseAndBackfill(raw, byId);
    }

    /**
     * 解析大模型返回并按 id 回填真实数据。
     * <p>
     * 这里是防「编造模型」的关键闸门：
     * <ul>
     *   <li>id 不在候选集 → 丢弃（模型 hallucinate 的 id 一定不在里面）</li>
     *   <li>展示字段全部取自 {@code byId}，不用模型返回的 name/status</li>
     *   <li>score 越界（<0 或 >1）夹紧到合法区间</li>
     * </ul>
     */
    private List<ModelRecommendVO> parseAndBackfill(String raw, Map<Long, ModelBriefVO> byId) {
        List<ModelRecommendVO> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }

        // 少数情况下模型仍会包一层 ```json ```，这里兜底剥掉
        String json = raw.trim();
        if (json.startsWith("```")) {
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }
        }

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode ranked = root.path("ranked");
            if (!ranked.isArray()) {
                return result;
            }
            for (JsonNode node : ranked) {
                long id = node.path("modelId").asLong(-1);
                ModelBriefVO brief = byId.get(id);
                if (brief == null) {
                    log.warn("推荐结果含非法模型 id={}，已丢弃（防编造）", id);
                    continue;
                }
                ModelRecommendVO vo = toRecommendVO(brief);
                double score = node.path("score").asDouble(0.5);
                vo.setScore(Math.max(0.0, Math.min(1.0, score)));
                vo.setReason(truncate(node.path("reason").asText(""), 200));
                result.add(vo);
            }
        } catch (Exception e) {
            log.warn("推荐结果解析失败，原始内容前 200 字: {}",
                    json.substring(0, Math.min(200, json.length())));
        }

        result.sort(Comparator.comparing(ModelRecommendVO::getScore).reversed());
        return result;
    }

    /**
     * 降级推荐：只按输入类型与关键词做匹配。
     * 明确标注「规则匹配」，不假装是语义分析结果。
     */
    private List<ModelRecommendVO> fallback(String requirement, List<ModelBriefVO> candidates) {
        String req = requirement == null ? "" : requirement.toLowerCase();
        String expectedInput = null;
        if (req.contains("图") || req.contains("照片") || req.contains("ct") || req.contains("image")) {
            expectedInput = "IMAGE";
        } else if (req.contains("文本") || req.contains("文字") || req.contains("评论")
                || req.contains("情感") || req.contains("text")) {
            expectedInput = "TEXT";
        } else if (req.contains("预测") || req.contains("数值") || req.contains("销量")
                || req.contains("天气") || req.contains("股票")) {
            expectedInput = "FEATURES";
        }

        String finalExpected = expectedInput;
        List<ModelRecommendVO> list = new ArrayList<>();
        for (ModelBriefVO c : candidates) {
            double score = 0.4;
            if (finalExpected != null && finalExpected.equals(c.getInputType())) {
                score = 0.7;
            }
            if (c.getName() != null && req.contains(c.getName().toLowerCase())) {
                score = 0.9;
            }
            if (score < 0.5) {
                continue;
            }
            ModelRecommendVO vo = toRecommendVO(c);
            vo.setScore(score);
            vo.setReason("按输入类型与关键词规则匹配（大模型暂不可用，未生成语义理由）");
            list.add(vo);
        }
        list.sort(Comparator.comparing(ModelRecommendVO::getScore).reversed());
        return list;
    }

    private ModelRecommendVO toRecommendVO(ModelBriefVO brief) {
        ModelRecommendVO vo = new ModelRecommendVO();
        vo.setId(brief.getId());
        vo.setName(brief.getName());
        vo.setType(brief.getType());
        vo.setTypeLabel(brief.getTypeLabel());
        vo.setAlgorithm(brief.getAlgorithm());
        vo.setAccuracy(brief.getAccuracy());
        vo.setStatus(brief.getStatus());
        vo.setDescription(brief.getDescription());
        vo.setInputType(brief.getInputType());
        vo.setRequiredFields(brief.getRequiredFields());
        return vo;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
