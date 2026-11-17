package com.aimodelhub.agent.service;

import com.aimodelhub.agent.entity.AgentModelProfile;
import com.aimodelhub.agent.mapper.AgentModelProfileMapper;
import com.aimodelhub.agent.vo.ModelBriefVO;
import com.aimodelhub.common.exception.BusinessException;
import com.aimodelhub.common.exception.NotFoundException;
import com.aimodelhub.model.entity.ModelInfo;
import com.aimodelhub.model.service.ModelService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 模型查询的「业务视图」层 —— 工具与推荐服务共用。
 *
 * <h3>为什么要再加一层，而不是让 Tool 直接调 ModelService</h3>
 * <ul>
 *   <li>{@link ModelService} 返回的是持久化实体 {@code ModelInfo}，
 *       字段包含 cover、createTime 等对 Agent 无用的噪声；</li>
 *   <li>Agent 需要的是「上线 + 输入规范 + 分类中文名」的组合视图；</li>
 *   <li>上下架过滤、输入规范缺失处理这些规则只能有一份，
 *       放在 Tool 里就会出现「列表工具过滤了、详情工具忘了过滤」。</li>
 * </ul>
 * 因此：<b>Tool 只做参数翻译，业务规则都在这里。</b>
 */
@Service
public class AgentModelQueryService {

    private static final Logger log = LoggerFactory.getLogger(AgentModelQueryService.class);

    /**
     * model_type → 中文标签。项目没有独立分类表，分类就是 model_type 的去重。
     * <p>
     * 注意 {@code image_advanced}：U2Net（抠图）用的是这个类型，
     * 与前端 {@code data/models.ts} 的映射保持一致。漏掉它会让 Agent 卡片上
     * 直接显示英文类型名。
     */
    private static final Map<String, String> TYPE_LABELS = Map.of(
            "image", "图像识别",
            "image_advanced", "图像处理",
            "object", "目标检测",
            "text", "文本处理",
            "ml", "结构化预测"
    );

    /** 用户说「图像模型」时，把 image 与 image_advanced 都算进来（都是图片输入）。 */
    private static final Map<String, List<String>> TYPE_ALIASES = Map.of(
            "image", List.of("image", "image_advanced")
    );

    private final ModelService modelService;
    private final AgentModelProfileMapper profileMapper;
    private final ObjectMapper objectMapper;

    public AgentModelQueryService(ModelService modelService,
                                  AgentModelProfileMapper profileMapper,
                                  ObjectMapper objectMapper) {
        this.modelService = modelService;
        this.profileMapper = profileMapper;
        this.objectMapper = objectMapper;
    }

    // ================================================================
    // 查询
    // ================================================================

    /**
     * 在线模型列表。
     *
     * @param type    可选，image / object / text / ml
     * @param keyword 可选，匹配名称、算法、简介
     */
    public List<ModelBriefVO> listOnline(String type, String keyword) {
        Map<Long, AgentModelProfile> profiles = profileMap();
        String typeFilter = normalize(type);
        String kw = normalize(keyword);
        // 别名展开：type=image 时同时匹配 image 与 image_advanced
        List<String> acceptedTypes = typeFilter == null
                ? null
                : TYPE_ALIASES.getOrDefault(typeFilter, List.of(typeFilter));

        return modelService.getOnlineModels().stream()
                .filter(m -> acceptedTypes == null
                        || acceptedTypes.contains(normalize(m.getModelType())))
                .filter(m -> kw == null || matches(m, kw))
                .map(m -> toBrief(m, profiles.get(m.getId())))
                .toList();
    }

    /**
     * 模型详情。<b>只允许查看在线模型</b>：
     * 直接复用 {@link ModelService#getModelById} 的缓存，但补一道上下架校验，
     * 否则 Agent 会把后台已下架的模型当成可用模型推荐给用户。
     */
    public ModelBriefVO detail(Long modelId) {
        ModelInfo model;
        try {
            model = modelService.getModelById(modelId);
        } catch (NotFoundException e) {
            throw new BusinessException("模型不存在：" + modelId);
        }
        if (!Integer.valueOf(ModelService.STATUS_ONLINE).equals(model.getStatus())) {
            throw new BusinessException("模型「" + model.getModelName() + "」当前未上线，无法使用");
        }
        return toBrief(model, profileMapper.findByModelId(modelId));
    }

    /** 分类列表：按 model_type 去重，带模型数量。 */
    public List<Map<String, Object>> categories() {
        Map<String, Long> counts = modelService.getOnlineModels().stream()
                .collect(Collectors.groupingBy(
                        m -> m.getModelType() == null ? "unknown" : m.getModelType(),
                        LinkedHashMap::new,
                        Collectors.counting()));
        List<Map<String, Object>> result = new ArrayList<>();
        counts.forEach((type, count) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", type);
            item.put("label", TYPE_LABELS.getOrDefault(type, type));
            item.put("modelCount", count);
            result.add(item);
        });
        return result;
    }

    /** 输入要求：给 Agent 判断「这个模型能不能处理用户手上的数据」。 */
    public Map<String, Object> inputRequirements(Long modelId) {
        ModelBriefVO brief = detail(modelId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("modelId", brief.getId());
        result.put("modelName", brief.getName());
        result.put("inputType", brief.getInputType());
        result.put("requiredFields", brief.getRequiredFields());
        AgentModelProfile profile = profileMapper.findByModelId(modelId);
        if (profile != null) {
            result.put("exampleInput", profile.getExampleInput());
            result.put("usageNote", profile.getUsageNote());
        }
        return result;
    }

    /** 供推荐服务使用：在线模型 + 输入规范一次性取出，避免 N+1 查询。 */
    public List<ModelBriefVO> allOnline() {
        Map<Long, AgentModelProfile> profiles = profileMap();
        return modelService.getOnlineModels().stream()
                .map(m -> toBrief(m, profiles.get(m.getId())))
                .toList();
    }

    public static String typeLabel(String type) {
        if (type == null) {
            return "未分类";
        }
        return TYPE_LABELS.getOrDefault(type, type);
    }

    // ================================================================
    // 内部
    // ================================================================

    private Map<Long, AgentModelProfile> profileMap() {
        return profileMapper.findAll().stream()
                .collect(Collectors.toMap(AgentModelProfile::getModelId, p -> p, (a, b) -> a));
    }

    private ModelBriefVO toBrief(ModelInfo model, AgentModelProfile profile) {
        ModelBriefVO vo = new ModelBriefVO();
        vo.setId(model.getId());
        vo.setName(model.getModelName());
        vo.setType(model.getModelType());
        vo.setTypeLabel(typeLabel(model.getModelType()));
        vo.setAlgorithm(model.getAlgorithm());
        vo.setAccuracy(model.getAccuracy());
        vo.setStatus(model.getStatus());
        vo.setDescription(truncate(model.getDescription(), 200));
        if (profile != null) {
            vo.setInputType(profile.getInputType());
            vo.setRequiredFields(parseFields(profile.getRequiredFields()));
        } else {
            // 没有输入规范时不要瞎猜：留空，让 Agent 明确告知用户「该模型缺少输入说明」
            vo.setInputType(null);
            vo.setRequiredFields(List.of());
        }
        return vo;
    }

    private List<String> parseFields(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.warn("agent_model_profile.required_fields 解析失败: {}", json);
            return List.of();
        }
    }

    private boolean matches(ModelInfo model, String keyword) {
        return contains(model.getModelName(), keyword)
                || contains(model.getAlgorithm(), keyword)
                || contains(model.getDescription(), keyword)
                || contains(model.getModelType(), keyword);
    }

    private static boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
