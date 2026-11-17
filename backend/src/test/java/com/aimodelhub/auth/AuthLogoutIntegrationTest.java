package com.aimodelhub.auth;

import com.aimodelhub.auth.dto.LoginDTO;
import com.aimodelhub.common.api.Result;
import com.aimodelhub.support.IntegrationTestUsers;
import com.aimodelhub.user.entity.SysUser;
import com.aimodelhub.user.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the JWT logout → blacklist → reject flow.
 * <p>
 * <b>Prerequisites:</b>
 * <ul>
 *   <li>MySQL running at {@code localhost:3306} (database {@code ai_modelhub})</li>
 *   <li>Redis running at {@code localhost:6379}</li>
 * </ul>
 * 测试账号由 {@link IntegrationTestUsers} 在运行时创建、结束后删除，
 * 不再依赖 {@code 002_init_seed.sql} 中的 admin/demo 种子数据。
 * <p>
 * Tests:
 * <ol>
 *   <li>login → access protected endpoint → logout → same token is rejected (403)</li>
 *   <li>Blacklist Redis key has correct TTL</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@DisplayName("Auth logout integration tests")
class AuthLogoutIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String baseUrl;

    /** 每个测试用例自建账号，互不干扰。 */
    private SysUser testUser;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        testUser = IntegrationTestUsers.create(sysUserMapper, passwordEncoder, "it_auth", "user");
    }

    @AfterEach
    void tearDown() {
        IntegrationTestUsers.delete(jdbcTemplate, testUser != null ? testUser.getId() : null);
    }

    // ─────────────────────────────────────────────────────────
    // Helper: login as admin and return the JWT token string
    // ─────────────────────────────────────────────────────────
    private String login(String username, String password) {
        LoginDTO dto = new LoginDTO();
        dto.username = username;
        dto.password = password;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<LoginDTO> request = new HttpEntity<>(dto, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                baseUrl + "/api/auth/login",
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(200, body.get("code"));

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertNotNull(data);
        assertNotNull(data.get("token"));

        return (String) data.get("token");
    }

    // ─────────────────────────────────────────────────────────
    // Helper: make authenticated GET request
    // ─────────────────────────────────────────────────────────
    private ResponseEntity<Map<String, Object>> authenticatedGet(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        return restTemplate.exchange(
                baseUrl + path,
                HttpMethod.GET,
                request,
                new ParameterizedTypeReference<>() {}
        );
    }

    // ─────────────────────────────────────────────────────────
    // Helper: call logout
    // ─────────────────────────────────────────────────────────
    private ResponseEntity<Map<String, Object>> logout(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        return restTemplate.exchange(
                baseUrl + "/api/auth/logout",
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<>() {}
        );
    }

    // ─────────────────────────────────────────────────────────
    // Test 1: login → access protected → logout → rejected
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Full flow: login → access API → logout → same token returns 403")
    void loginAccessLogoutThenRejected() {
        // Step 1: Login
        String token = login(testUser.getUsername(), IntegrationTestUsers.PASSWORD);

        // Step 2: Access a protected endpoint (GET /api/users/me)
        ResponseEntity<Map<String, Object>> profileResp = authenticatedGet("/api/users/me", token);
        assertEquals(HttpStatus.OK, profileResp.getStatusCode());
        Map<String, Object> profileBody = profileResp.getBody();
        assertNotNull(profileBody);
        assertEquals(200, profileBody.get("code"));

        // Step 3: Logout
        ResponseEntity<Map<String, Object>> logoutResp = logout(token);
        assertEquals(HttpStatus.OK, logoutResp.getStatusCode());
        Map<String, Object> logoutBody = logoutResp.getBody();
        assertNotNull(logoutBody);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) logoutBody.get("data");
        assertNotNull(data);
        assertEquals(true, data.get("blacklisted"),
                "Expected blacklisted=true, got reason=" + data.get("reason"));

        // Step 4: Same token should now be rejected (403 Forbidden)
        // The JwtAuthFilter clears SecurityContext for blacklisted tokens,
        // so the request proceeds unauthenticated → SecurityConfig denies access.
        ResponseEntity<Map<String, Object>> rejectedResp = authenticatedGet("/api/users/me", token);
        assertTrue(
                rejectedResp.getStatusCode() == HttpStatus.FORBIDDEN ||
                rejectedResp.getStatusCode() == HttpStatus.UNAUTHORIZED,
                "Expected 403 or 401 after logout, got " + rejectedResp.getStatusCode()
        );
    }

    // ─────────────────────────────────────────────────────────
    // Test 2: Blacklist Redis key TTL is reasonable
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Blacklisted jti in Redis has TTL > 0 and <= 24h")
    void blacklistKeyHasCorrectTtl() {
        // Login
        String token = login(testUser.getUsername(), IntegrationTestUsers.PASSWORD);

        // Logout — this blacklists the jti
        ResponseEntity<Map<String, Object>> logoutResp = logout(token);
        assertEquals(HttpStatus.OK, logoutResp.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) logoutResp.getBody().get("data");
        assertNotNull(data);

        if (Boolean.TRUE.equals(data.get("blacklisted"))) {
            // Find the blacklist key in Redis
            var keys = redisTemplate.keys("jwt:blacklist:*");
            assertNotNull(keys);
            assertFalse(keys.isEmpty(), "At least one blacklist key should exist");

            // Check TTL on the first key (there should be exactly one from this test)
            for (String key : keys) {
                Long ttlSeconds = redisTemplate.getExpire(key);
                assertNotNull(ttlSeconds);
                assertTrue(ttlSeconds > 0,
                        "Blacklist key TTL should be > 0, got " + ttlSeconds + "s");
                assertTrue(ttlSeconds <= 86400, // 24 hours in seconds
                        "Blacklist key TTL should be <= 24h (token max lifetime), got " + ttlSeconds + "s");
                break;
            }

            // Cleanup: remove blacklist keys created by this test
            for (String key : keys) {
                redisTemplate.delete(key);
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // Test 3: Logout with no token → 403 (endpoint requires auth)
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Logout without token returns 403")
    void logoutWithoutTokenReturns403() {
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                baseUrl + "/api/auth/logout",
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<>() {}
        );

        assertTrue(
                response.getStatusCode() == HttpStatus.FORBIDDEN ||
                response.getStatusCode() == HttpStatus.UNAUTHORIZED,
                "Logout without token should return 403/401, got " + response.getStatusCode()
        );
    }
}
