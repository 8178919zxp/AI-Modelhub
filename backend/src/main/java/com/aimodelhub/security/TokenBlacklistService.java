package com.aimodelhub.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 基于 Redis 的 JWT 令牌黑名单。
 * 用户退出登录（或管理员吊销令牌）时，会把令牌的 {@code jti}（JWT ID）写入 Redis，
 * TTL 等于该令牌的剩余有效期。{@link JwtAuthFilter} 会在每次认证请求时检查这份黑名单。
 * 启用状态：功能已完整实现，过滤器中的黑名单校验由
 * {@code app.jwt.blacklist-enabled} 控制（默认 {@code false}），避免影响既有行为。
 * 在提供调用 {@link #blacklist} 的退出登录接口后再开启。
 * Redis key 设计
 *   jwt:blacklist:{jti} → "1"  （TTL = 令牌剩余有效期）
 * 使用简单的 key-value（而不是 Set），过期记录由 Redis 自动删除。
 * 优雅降级
 * Redis 不可达时，黑名单操作只打印告警并返回安全默认值：
 * {@link #isBlacklisted} 返回 {@code false}（放行请求），{@link #blacklist} 不执行任何操作。
 */
@Service
public class TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);

    private static final String KEY_PREFIX = "jwt:blacklist:";
    private static final String BLACKLIST_VALUE = "1";

    /**
     * 按用户的吊销时间点：{@code jwt:user-revoked-before:{userId} -> epochMillis}。
     * 早于该时间点签发的令牌一律拒绝。用于密码修改之后，
     * 确保被窃取的令牌不会在改密后继续有效。
     */
    private static final String USER_CUTOFF_PREFIX = "jwt:user-revoked-before:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final boolean blacklistEnabled;

    public TokenBlacklistService(RedisTemplate<String, Object> redisTemplate,
                                 @Value("${app.jwt.blacklist-enabled:false}") boolean blacklistEnabled) {
        this.redisTemplate = redisTemplate;
        this.blacklistEnabled = blacklistEnabled;
        log.info("TokenBlacklistService initialized (enabled={})", blacklistEnabled);
    }

    /**
     * 过滤器中的黑名单校验是否生效。
     */
    public boolean isEnabled() {
        return blacklistEnabled;
    }

    /**
     * 把令牌的 jti 写入黑名单。
     * 黑名单记录会与令牌本身同时过期。
     *
     * @param jti          令牌的 JWT ID
     * @param expirationMs 剩余有效期（毫秒）
     */
    public void blacklist(String jti, long expirationMs) {
        if (jti == null || expirationMs <= 0) return;

        String key = KEY_PREFIX + jti;
        try {
            redisTemplate.opsForValue().set(key, BLACKLIST_VALUE, Duration.ofMillis(expirationMs));
            log.debug("Token blacklisted: jti={}, expiresIn={}ms", jti, expirationMs);
        } catch (Exception e) {
            log.warn("Failed to blacklist token jti={}: {}", jti, e.getMessage());
        }
    }

    /**
     * 判断某个令牌的 jti 是否已在黑名单中。
     * 以下情况一律返回 {@code false}（放行）：
     *   配置中关闭了黑名单
     *   Redis 不可达
     *   jti 为 null（不带 jti 声明的旧令牌）
     * @param jti 待检查的 JWT ID
     * @return 令牌已被吊销时返回 true
     */
    public boolean isBlacklisted(String jti) {
        if (!blacklistEnabled) return false;
        if (jti == null) return false;

        String key = KEY_PREFIX + jti;
        try {
            Boolean exists = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.warn("Failed to check blacklist for jti={}: {} — allowing request through", jti, e.getMessage());
            return false;
        }
    }

    /**
     * 吊销某用户在 {@code revokedBeforeMillis} 之前签发的全部令牌。
     * 在修改密码（用户自助重置或管理员重置）之后调用，
     * 使此前签发的令牌立即失效。TTL 为 7 天，远大于令牌的最长有效期。
     * 优雅降级：Redis 不可达时该调用不执行任何操作，只打印告警，
     * 令牌会一直有效到自然过期。
     */
    public void revokeAllForUser(Long userId, long revokedBeforeMillis) {
        if (userId == null) return;
        String key = USER_CUTOFF_PREFIX + userId;
        try {
            redisTemplate.opsForValue()
                    .set(key, String.valueOf(revokedBeforeMillis), Duration.ofDays(7));
            log.info("Revoked all tokens for userId={} issued before {}", userId, revokedBeforeMillis);
        } catch (Exception e) {
            log.warn("Failed to revoke tokens for userId={}: {}", userId, e.getMessage());
        }
    }

    /**
     * 判断签发时间为 {@code issuedAtMillis} 的令牌是否早于该用户的吊销时间点。
     * JWT 的 {@code iat} 只精确到秒，因此这里留 1 秒容差；
     * 否则改密后立刻签发的令牌可能被误判为更早的令牌。
     * Redis 异常时放行（fail-open），与 {@link #isBlacklisted(String)} 保持一致。
     */
    public boolean isIssuedBeforeRevocation(Long userId, long issuedAtMillis) {
        if (userId == null) return false;
        try {
            Object value = redisTemplate.opsForValue().get(USER_CUTOFF_PREFIX + userId);
            if (value == null) return false;
            long cutoff = Long.parseLong(String.valueOf(value));
            return issuedAtMillis + 1000L < cutoff;
        } catch (Exception e) {
            log.warn("Failed to check revocation cutoff for userId={}: {} — allowing request",
                    userId, e.getMessage());
            return false;
        }
    }

    /**
     * 把某个 jti 从黑名单中移除（用于管理员撤销吊销）。
     */
    public void remove(String jti) {
        if (jti == null) return;
        String key = KEY_PREFIX + jti;
        try {
            redisTemplate.delete(key);
            log.debug("Token removed from blacklist: jti={}", jti);
        } catch (Exception e) {
            log.warn("Failed to remove token from blacklist jti={}: {}", jti, e.getMessage());
        }
    }

    /**
     * 统计黑名单中的令牌数量，供监控使用。
     * Redis 不可达时返回 -1。
     */
    public long count() {
        try {
            // 用 SCAN 而不是 KEYS：KEYS 是 O(N) 操作，会阻塞 Redis 的单线程，
            // 连带拖慢缓存与限流请求。
            Long count = redisTemplate.execute((RedisCallback<Long>) connection -> {
                long total = 0;
                ScanOptions options = ScanOptions.scanOptions()
                        .match(KEY_PREFIX + "*")
                        .count(500)
                        .build();
                try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                    while (cursor.hasNext()) {
                        cursor.next();
                        total++;
                    }
                }
                return total;
            });
            return count != null ? count : -1;
        } catch (Exception e) {
            log.warn("Failed to count blacklisted tokens: {}", e.getMessage());
            return -1;
        }
    }
}
