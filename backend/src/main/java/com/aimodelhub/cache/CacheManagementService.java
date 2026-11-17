package com.aimodelhub.cache;

import com.aimodelhub.config.CacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 缓存清理的统一入口（按业务动作手动清理）。
 * <p>
 * 模型缓存（{@code models::online}、{@code model_detail::{id}}）由
 * {@code ModelService} 上的 {@code @CacheEvict} 声明式清理，不经过本类。
 * 本类只保留需要「按动作触发」的两类缓存：
 * <ul>
 *   <li>统计概览 {@code dashboard_overview} —— 模型增删改后必须刷新</li>
 *   <li>用户预测记录 {@code user_records::{userId}} —— 新记录落库后必须失效</li>
 * </ul>
 * Redis 不可达时这些方法也不会抛错：{@link CacheConfig} 中配置的
 * {@link org.springframework.cache.interceptor.CacheErrorHandler}
 * 会打印告警并吞掉异常。
 *
 * <h3>调用示例</h3>
 * <pre>
 * // 模型增删改后：
 *   cacheManagementService.evictDashboardCache();
 *
 * // 用户产生新预测记录后：
 *   cacheManagementService.evictUserRecordsCache(userId);
 * </pre>
 */
@Service
public class CacheManagementService {

    private static final Logger log = LoggerFactory.getLogger(CacheManagementService.class);

    private final CacheManager cacheManager;
    private final CacheMetricsService metricsService;

    public CacheManagementService(CacheManager cacheManager,
                                   CacheMetricsService metricsService) {
        this.cacheManager = cacheManager;
        this.metricsService = metricsService;
    }

    /**
     * 清理数据统计概览缓存。
     * 模型变更或批量预测后调用。
     */
    public void evictDashboardCache() {
        try {
            Objects.requireNonNull(cacheManager.getCache(CacheConfig.CACHE_DASHBOARD))
                    .evict("overview");
            metricsService.recordEviction(CacheConfig.CACHE_DASHBOARD);
            log.debug("Evicted cache: {}::overview", CacheConfig.CACHE_DASHBOARD);
        } catch (Exception e) {
            log.warn("Failed to evict dashboard cache: {}", e.getMessage());
        }
    }

    /**
     * 清理某个用户的预测记录缓存。
     * 该用户产生新预测记录后调用。
     */
    public void evictUserRecordsCache(Long userId) {
        if (userId == null) return;
        try {
            Objects.requireNonNull(cacheManager.getCache(CacheConfig.CACHE_USER_RECORDS))
                    .evict(userId);
            metricsService.recordEviction(CacheConfig.CACHE_USER_RECORDS);
            log.debug("Evicted cache: {}::{}", CacheConfig.CACHE_USER_RECORDS, userId);
        } catch (Exception e) {
            log.warn("Failed to evict user records cache for userId={}: {}", userId, e.getMessage());
        }
    }
}
