package com.aimodelhub.agent.tool;

import com.aimodelhub.agent.service.AgentInferenceService;
import com.aimodelhub.agent.service.AgentKnowledgeService;
import com.aimodelhub.agent.service.AgentModelQueryService;
import com.aimodelhub.agent.service.AgentModelRecommendService;
import com.aimodelhub.predict.service.PredictRecordService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * 工具装配工厂。
 *
 * <h3>为什么要有个工厂，而不是把这些类都标 @Component</h3>
 * 工具方法需要「当前用户」，而用户身份是请求级的。若工具是单例 Bean，
 * 就只能把 userId 塞进方法参数（大模型可以伪造）或塞进 ThreadLocal
 * （工具可能在 reactor 线程执行，ThreadLocal 不保证传得到）。
 * <p>
 * 所以：<b>依赖（Service）是单例，工具对象是请求级</b>。
 * 工厂持有单例依赖，按请求 new 出工具实例，把 userId 固定进对象状态。
 * <p>
 * 后续阶段新增「推理工具」时，只需要在这里多加一行。
 */
@Component
public class AgentToolKit {

    private final AgentModelQueryService queryService;
    private final AgentModelRecommendService recommendService;
    private final PredictRecordService predictRecordService;
    private final AgentInferenceService inferenceService;
    private final AgentKnowledgeService knowledgeService;
    private final ObjectMapper objectMapper;

    public AgentToolKit(AgentModelQueryService queryService,
                        AgentModelRecommendService recommendService,
                        PredictRecordService predictRecordService,
                        AgentInferenceService inferenceService,
                        AgentKnowledgeService knowledgeService,
                        ObjectMapper objectMapper) {
        this.queryService = queryService;
        this.recommendService = recommendService;
        this.predictRecordService = predictRecordService;
        this.inferenceService = inferenceService;
        this.knowledgeService = knowledgeService;
        this.objectMapper = objectMapper;
    }

    /** 为一次对话请求构造工具集合。 */
    public Object[] build(AgentToolContext context) {
        return new Object[]{
                new ModelQueryTool(context, queryService),
                new ModelRecommendTool(context, recommendService),
                new InferenceTool(context, predictRecordService),
                new ModelPredictTool(context, inferenceService, objectMapper),
                new KnowledgeTool(context, knowledgeService)
        };
    }
}
