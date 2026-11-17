package com.aimodelhub.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * RedisTemplate 配置。
 * <p>
 * 使用 Jackson JSON 序列化（不是 JDK 序列化），缓存内容在 Redis 中可读，
 * 且实体类无需实现 Serializable。Java 8 日期时间类型（LocalDateTime 等）
 * 由 JavaTimeModule 处理。
 * <p>
 * <b>重要：</b>这里使用的 {@link ObjectMapper} <em>不会</em>注册为 Spring Bean。
 * 它是一个开启了 {@code activateDefaultTyping} 的独立实例（从 Redis 反序列化泛型
 * 所必需）。如果注册为 Bean，Spring Boot 的 HTTP 消息转换器会复用它，
 * 导致 {@code @class} 类型元数据泄漏到接口响应中。
 * <p>
 * 说明：本 RedisTemplate 用于手动 Redis 操作（例如令牌黑名单）。
 * Spring Cache 使用 {@link CacheConfig} 中单独配置的 CacheManager。
 */
@Configuration
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    /**
     * 创建 Redis 序列化专用的 ObjectMapper，开启 {@code activateDefaultTyping}。
     * 不作为 Spring Bean 暴露。
     */
    static ObjectMapper createRedisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // 注册 Java 8 日期时间模块（LocalDateTime 等）
        mapper.registerModule(new JavaTimeModule());

        // 日期不序列化成时间戳，改用 ISO-8601 字符串
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 反序列化时忽略未知字段（兼容后续新增字段）
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // 写入类型信息，反序列化时才能还原成正确的具体类型。
        // 必须用 EVERYTHING（而不是 NON_FINAL），原因：
        // - DashboardService 返回 Map.of() → ImmutableCollections.MapN（final 类型）
        // - NON_FINAL 会跳过 final 类型的 @class → 反序列化失败
        // - EVERYTHING 始终写入 @class → 保证完整往返
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY
        );

        return mapper;
    }

    /**
     * key 为字符串、value 为 JSON 的 RedisTemplate。
     * 用于手动缓存操作（令牌黑名单、缓存管理等）。
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        log.info("Configuring RedisTemplate with GenericJackson2JsonRedisSerializer");

        GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer(createRedisObjectMapper());

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();

        return template;
    }
}
