package com.aimodelhub.dashboard.service;

import com.aimodelhub.config.CacheConfig;
import com.aimodelhub.model.service.ModelService;
import com.aimodelhub.predict.service.PredictRecordService;
import com.aimodelhub.user.service.UserService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private final ModelService modelService;
    private final UserService userService;
    private final PredictRecordService predictRecordService;

    public DashboardService(ModelService modelService,
                            UserService userService,
                            PredictRecordService predictRecordService) {
        this.modelService = modelService;
        this.userService = userService;
        this.predictRecordService = predictRecordService;
    }

    /**
     * 数据统计概览。
     * <p>
     * 缓存在 {@code cache:dashboard_overview::overview}，TTL 较短（默认 2 分钟），
     * 在实时性与性能之间取平衡。该接口聚合了模型、用户、预测记录三类数据，
     * 各自更新频率不同，因此短 TTL 更像是「读取节流」，而不是权威数据缓存。
     * <p>
     * 缓存清理：模型新增 / 更新 / 删除，或批量预测之后，
     * 调用 {@code CacheManagementService.evictDashboardCache()}。
     */
    @Cacheable(value = CacheConfig.CACHE_DASHBOARD, key = "'overview'")
    public Map<String, Object> getOverview() {
        // 注意：这里必须用可变集合。缓存值会用 Jackson 默认类型信息序列化，
        // Map.of() / List.of() 这类不可变集合无法干净地反序列化，
        // 会残留一个 "@class" 字段并最终泄漏到 HTTP 响应里。
        List<String> hotModels = modelService.getOnlineModels().stream()
                .sorted((a, b) -> Double.compare(
                        b.getAccuracy() != null ? b.getAccuracy() : 0,
                        a.getAccuracy() != null ? a.getAccuracy() : 0))
                .limit(3)
                .map(m -> m.getModelName())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("totalModels", modelService.getTotalModelCount());
        overview.put("onlineModels", modelService.getOnlineModelCount());
        overview.put("totalUsers", userService.getUserCount());
        overview.put("totalCalls", predictRecordService.getTotalRecordCount());
        overview.put("hotModels", hotModels);
        return overview;
    }
}
