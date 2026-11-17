package com.aimodelhub.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 基于 Redis 的 Spring Cache 配置。
 * <p>
 * 几个关键设计：
 * <ul>
 *   <li>使用 Jackson JSON 序列化（不是 JDK 序列化）——缓存内容可读，
 *       且实体类不必实现 {@code Serializable}。</li>
 *   <li>优雅降级：Redis 不可达时只打印告警并放行原方法（cache-aside 兜底），
 *       应用不会因为 Redis 故障而崩溃。</li>
 *   <li>按缓存名配置各自的 TTL，通过 {@code RedisCacheManagerBuilderCustomizer}
 *       或显式的 per-cache 配置表实现。</li>
 * </ul>
 *
 * <h3>缓存 key 命名约定</h3>
 * <pre>
 *   cache:models::online                 → GET /api/models 列表（仅在线模型）
 *   cache:model_detail::{id}             → GET /api/models/{id} 详情
 *   cache:dashboard:overview             → GET /api/dashboard/overview
 *   cache:users:records:{userId}         → GET /api/users/records
 *
 *   jwt:blacklist:{jti}                  → JWT 令牌黑名单（手动使用 RedisTemplate）
 * </pre>
 * <p>
 * 所有 key 都会加上 application.yml 中 {@code spring.cache.redis.key-prefix} 的前缀：
 * 开发环境是 {@code aimh:dev:}，生产环境是 {@code aimh:prod:}。
 *
 * @see RedisConfig 手动操作 Redis 时使用的 RedisTemplate
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    /** 配置未单独指定时，Spring Cache 使用的默认 TTL */
    @Value("${spring.cache.redis.time-to-live:300000}")
    private long defaultTtlMillis;

    /**
     * key 前缀（如 {@code aimh:dev:}）。这里显式应用，是因为本类自行构建了
     * RedisCacheManager，绕过了 Spring Boot 自动配置中负责应用该属性的 customizer。
     */
    @Value("${spring.cache.redis.key-prefix:}")
    private String keyPrefix;

    // ---- 缓存名常量（@Cacheable 注解中也引用这些值） ----
    public static final String CACHE_MODELS = "models";
    public static final String CACHE_MODEL_DETAIL = "model_detail";
    public static final String CACHE_DASHBOARD = "dashboard_overview";
    public static final String CACHE_USER_RECORDS = "user_records";

    // ---- 各缓存 TTL 覆盖配置（单位：毫秒） ----
    private static final long TTL_MODELS              = Duration.ofMinutes(5).toMillis();
    private static final long TTL_MODEL_DETAIL        = Duration.ofMinutes(10).toMillis();
    private static final long TTL_DASHBOARD_OVERVIEW  = Duration.ofMinutes(2).toMillis();
    private static final long TTL_USER_RECORDS        = Duration.ofMinutes(1).toMillis();

    /**
     * 构建 {@link RedisCacheManager}，使用 Jackson JSON 序列化，
     * 并按缓存名配置不同的 TTL。
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        log.info("Initializing RedisCacheManager (default TTL={}ms)", defaultTtlMillis);

        // Redis 专用的 ObjectMapper —— 不是 Spring MVC 的 HTTP ObjectMapper
        ObjectMapper redisObjectMapper = RedisConfig.createRedisObjectMapper();

        // ---- JSON 序列化器（key 为字符串，value 为 JSON） ----
        GenericJackson2JsonRedisSerializer valueSerializer =
                new GenericJackson2JsonRedisSerializer(redisObjectMapper);

        RedisSerializationContext.SerializationPair<String> keySerializationPair =
                RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer());

        RedisSerializationContext.SerializationPair<Object> valueSerializationPair =
                RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer);

        // ---- 默认配置 ----
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith(keyPrefix == null ? "" : keyPrefix)
                .entryTtl(Duration.ofMillis(defaultTtlMillis))
                .serializeKeysWith(keySerializationPair)
                .serializeValuesWith(valueSerializationPair)
                .disableCachingNullValues();

        // ---- 逐缓存的 TTL 映射 ----
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put(CACHE_MODELS,
                defaultConfig.entryTtl(Duration.ofMillis(TTL_MODELS)));
        cacheConfigs.put(CACHE_MODEL_DETAIL,
                defaultConfig.entryTtl(Duration.ofMillis(TTL_MODEL_DETAIL)));
        cacheConfigs.put(CACHE_DASHBOARD,
                defaultConfig.entryTtl(Duration.ofMillis(TTL_DASHBOARD_OVERVIEW)));
        cacheConfigs.put(CACHE_USER_RECORDS,
                defaultConfig.entryTtl(Duration.ofMillis(TTL_USER_RECORDS)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }

    /**
     * 优雅降级：Redis 操作失败（连接被拒、超时等）时只打印告警，
     * 让原方法继续执行。
     * <p>
     * 这是「可控降级」的关键——Redis 挂掉不会拖垮应用，
     * 每次缓存未命中都会回落到下层服务 → 数据库查询。
     */
    @Bean
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, org.springframework.cache.Cache cache, Object key) {
                log.warn("Redis GET error on cache={} key={}: {} — falling back to method execution",
                        cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, org.springframework.cache.Cache cache, Object key, Object value) {
                log.warn("Redis PUT error on cache={} key={}: {} — value not cached, will retry next call",
                        cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, org.springframework.cache.Cache cache, Object key) {
                log.warn("Redis EVICT error on cache={} key={}: {} — stale cache may persist",
                        cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, org.springframework.cache.Cache cache) {
                log.warn("Redis CLEAR error on cache={}: {} — stale cache may persist",
                        cache.getName(), e.getMessage());
            }
        };
    }
}
