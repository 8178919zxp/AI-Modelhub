package com.aimodelhub.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TokenBlacklistService}.
 * <p>
 * These tests mock {@link RedisTemplate} and verify:
 * <ul>
 *   <li>Blacklisting a token writes to Redis with correct TTL</li>
 *   <li>Blacklist check returns true for blacklisted token</li>
 *   <li>Graceful degradation when Redis is unavailable</li>
 *   <li>Edge cases: null jti, negative TTL, disabled blacklist</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TokenBlacklistService unit tests")
class TokenBlacklistServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private TokenBlacklistService service;
    private TokenBlacklistService disabledService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // Default: blacklist enabled
        service = new TokenBlacklistService(redisTemplate, true);

        // Disabled variant
        disabledService = new TokenBlacklistService(redisTemplate, false);
    }

    // ──────────────────────────────────────────────
    // isEnabled
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("isEnabled returns true when blacklist is enabled")
    void isEnabledTrue() {
        assertTrue(service.isEnabled());
    }

    @Test
    @DisplayName("isEnabled returns false when blacklist is disabled")
    void isEnabledFalse() {
        assertFalse(disabledService.isEnabled());
    }

    // ──────────────────────────────────────────────
    // blacklist — normal cases
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("blacklist stores jti in Redis with correct TTL")
    void blacklistNormalCase() {
        String jti = "test-jti-uuid";
        long expirationMs = 3600_000; // 1 hour

        service.blacklist(jti, expirationMs);

        verify(valueOperations).set(
                eq("jwt:blacklist:test-jti-uuid"),
                eq("1"),
                eq(Duration.ofMillis(expirationMs))
        );
    }

    @Test
    @DisplayName("blacklist with zero TTL is a no-op")
    void blacklistZeroTtl() {
        service.blacklist("test-jti", 0);
        verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
    }

    @Test
    @DisplayName("blacklist with negative TTL is a no-op")
    void blacklistNegativeTtl() {
        service.blacklist("test-jti", -100);
        verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
    }

    @Test
    @DisplayName("blacklist with null jti is a no-op")
    void blacklistNullJti() {
        service.blacklist(null, 60000);
        verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
    }

    // ──────────────────────────────────────────────
    // blacklist — Redis failure
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("blacklist does not throw when Redis is down")
    void blacklistRedisFailureDoesNotThrow() {
        doThrow(new RuntimeException("Connection refused"))
                .when(valueOperations).set(anyString(), any(), any(Duration.class));

        // Should not throw — graceful degradation
        assertDoesNotThrow(() -> service.blacklist("test-jti", 60000));
    }

    // ──────────────────────────────────────────────
    // isBlacklisted
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("isBlacklisted returns true when key exists in Redis")
    void isBlacklistedTrue() {
        when(redisTemplate.hasKey("jwt:blacklist:test-jti")).thenReturn(true);

        assertTrue(service.isBlacklisted("test-jti"));
    }

    @Test
    @DisplayName("isBlacklisted returns false when key does not exist")
    void isBlacklistedFalse() {
        when(redisTemplate.hasKey("jwt:blacklist:test-jti")).thenReturn(false);

        assertFalse(service.isBlacklisted("test-jti"));
    }

    @Test
    @DisplayName("isBlacklisted returns false when blacklist is disabled")
    void isBlacklistedWhenDisabled() {
        // No Redis call should happen
        assertFalse(disabledService.isBlacklisted("test-jti"));
        verify(redisTemplate, never()).hasKey(anyString());
    }

    @Test
    @DisplayName("isBlacklisted returns false for null jti")
    void isBlacklistedNullJti() {
        assertFalse(service.isBlacklisted(null));
        verify(redisTemplate, never()).hasKey(anyString());
    }

    @Test
    @DisplayName("isBlacklisted returns false when Redis is down (safe default)")
    void isBlacklistedRedisDown() {
        when(redisTemplate.hasKey(anyString()))
                .thenThrow(new RuntimeException("Connection refused"));

        // Should return false (allow request) rather than throwing
        assertFalse(service.isBlacklisted("test-jti"));
    }

    // ──────────────────────────────────────────────
    // remove
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("remove deletes key from Redis")
    void removeNormal() {
        service.remove("test-jti");
        verify(redisTemplate).delete("jwt:blacklist:test-jti");
    }

    @Test
    @DisplayName("remove with null jti is a no-op")
    void removeNullJti() {
        service.remove(null);
        verify(redisTemplate, never()).delete(anyString());
    }

    // ──────────────────────────────────────────────
    // count
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("count returns -1 when Redis is down")
    void countRedisDown() {
        // count() 内部用 SCAN（redisTemplate.execute(RedisCallback)）而不是 KEYS，
        // 所以这里必须让 execute 抛异常；原先 stub 的 keys() 压根不会被调用，
        // 在 Mockito 严格模式下会报 UnnecessaryStubbing。
        doThrow(new RuntimeException("Connection refused"))
                .when(redisTemplate).execute(any(RedisCallback.class));

        assertEquals(-1, service.count());
    }
}
