package com.aimodelhub.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通过 Micrometer 暴露缓存运行指标。
 * <p>
 * 计数器按缓存名打标签，因此在 Prometheus / {@code /actuator/metrics} 中
 * 会呈现为多条时间序列：
 * <pre>
 *   cache.evictions{cache="models"}            — 模型列表淘汰次数
 *   cache.evictions{cache="model_detail"}      — 模型详情淘汰次数
 *   cache.evictions{cache="dashboard_overview"}— 统计概览淘汰次数
 *   cache.evictions{cache="user_records"}      — 用户记录淘汰次数
 * </pre>
 * <p>
 * Spring Boot 内置的 {@code CacheMetricsRegistrar} 也会自动绑定
 * {@code cache.gets}、{@code cache.puts}、{@code cache.evictions}，
 * 反映的是全部缓存操作。这里额外统计的是「有明确意图的清理」，
 * 用于区分「后台操作触发的淘汰」与「Redis TTL 自然过期」
 * （后者只会出现在自动绑定的指标里）。
 */
@Service
public class CacheMetricsService {

    private static final Logger log = LoggerFactory.getLogger(CacheMetricsService.class);

    private final MeterRegistry meterRegistry;
    private final Map<String, Counter> evictionCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> missCounters = new ConcurrentHashMap<>();

    public CacheMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    void init() {
        log.info("CacheMetricsService initialized — eviction + miss counters registered");
    }

    /**
     * 记录一次手动的单条缓存清理。
     * @param cacheName 被清理的缓存名（如 "models"、"model_detail"）
     */
    public void recordEviction(String cacheName) {
        counter(cacheName).increment();
    }

    /**
     * 记录一次缓存未命中：当 {@code @Cacheable} 方法因为缓存里没有对应 key
     * 而真正执行方法体时调用。
     * @param cacheName 未命中的缓存名（如 "models"、"model_detail"）
     */
    public void recordMiss(String cacheName) {
        missCounter(cacheName).increment();
    }

    // ── 内部实现 ───────────────────────────────────────────────────

    private Counter counter(String cacheName) {
        return evictionCounters.computeIfAbsent(cacheName, name ->
                Counter.builder("cache.evictions.manual")
                        .description("Manual cache evictions triggered by application logic")
                        .tag("cache", name)
                        .register(meterRegistry)
        );
    }

    private Counter missCounter(String cacheName) {
        return missCounters.computeIfAbsent(cacheName, name ->
                Counter.builder("cache.misses")
                        .description("Cache misses detected by AOP interceptor on @Cacheable methods")
                        .tag("cache", name)
                        .register(meterRegistry)
        );
    }
}
