package com.aimodelhub.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redis 滑动窗口限流器。
 *
 * <h3>算法</h3>
 * 使用 Redis Sorted Set（ZSET）实现真正的滑动窗口：
 * <ol>
 *   <li>ZREMRANGEBYSCORE — 移除窗口外的过期请求</li>
 *   <li>ZCARD — 统计窗口内剩余请求数</li>
 *   <li>若 &ge; 阈值 → 拒绝</li>
 *   <li>ZADD — 将当前请求加入窗口</li>
 * </ol>
 *
 * <p>
 * 与固定窗口（INCR + EXPIRE）相比，滑动窗口不会在窗口边界出现
 * "两倍突发流量"问题，限流更平滑；同时计数不会依赖单独的 EXPIRE，
 * 即使 TTL 丢失，窗口外的成员也会在下一次调用时被清理，不会出现
 * "计数只增不减导致永久限流"的问题。
 *
 * <p>
 * <b>原子性：</b>判定与写入在同一条 Lua 脚本中完成，避免"多个请求同时
 * 读到相同计数"导致超发。
 *
 * <p>
 * Redis Key 格式: {@code rate:limit:{identifier}}
 * <br>
 * ZSET Member: {@code {timestamp}:{threadId}}
 * <br>
 * ZSET Score: 当前毫秒时间戳
 */
@Component
public class SlidingWindowRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(SlidingWindowRateLimiter.class);

    private static final String KEY_PREFIX = "rate:limit:";

    /**
     * KEYS[1] = zset 的 key
     * ARGV[1] = 当前时间（毫秒）
     * ARGV[2] = 窗口长度（毫秒）
     * ARGV[3] = 允许的最大请求数
     * ARGV[4] = 成员值（当前时间:线程号）
     * ARGV[5] = key 的过期时间（秒）
     *
     * @return 1 = 放行，0 = 拒绝
     */
    private static final String ALLOW_LUA = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local windowMs = tonumber(ARGV[2])
            local maxRequests = tonumber(ARGV[3])
            local member = ARGV[4]
            local ttlSeconds = tonumber(ARGV[5])

            redis.call('ZREMRANGEBYSCORE', key, 0, now - windowMs)
            local count = redis.call('ZCARD', key)
            if count >= maxRequests then
              redis.call('EXPIRE', key, ttlSeconds)
              return 0
            end
            redis.call('ZADD', key, now, member)
            redis.call('EXPIRE', key, ttlSeconds)
            return 1
            """;

    private static final RedisScript<Long> ALLOW_SCRIPT =
            new DefaultRedisScript<>(ALLOW_LUA, Long.class);

    private final StringRedisTemplate redisTemplate;

    public SlidingWindowRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 检查请求是否允许通过（原子操作）。
     *
     * @param identifier    唯一标识符（如 {@code predict:user:123} 或 {@code predict:ip:192.168.1.1}）
     * @param maxRequests   时间窗口内允许的最大请求数
     * @param windowSeconds 时间窗口（秒）
     * @return {@code true} 允许通过，{@code false} 触发限流
     */
    public boolean isAllowed(String identifier, int maxRequests, int windowSeconds) {
        String redisKey = KEY_PREFIX + identifier;
        long now = System.currentTimeMillis();
        // 成员必须唯一：时间戳 + 线程号，避免同一毫秒内同一线程重复写入
        String member = now + ":" + Thread.currentThread().getId();

        Long allowed = redisTemplate.execute(
                ALLOW_SCRIPT,
                List.of(redisKey),
                String.valueOf(now),
                String.valueOf(windowSeconds * 1000L),
                String.valueOf(maxRequests),
                member,
                String.valueOf(windowSeconds + 10));

        // 返回 null 表示脚本没有返回（极端情况下 Redis 断连会直接抛异常，不会走到这里）
        if (allowed == null || allowed == 0L) {
            log.info("限流触发 | key={} | max={} | window={}s",
                    identifier, maxRequests, windowSeconds);
            return false;
        }
        return true;
    }

}
