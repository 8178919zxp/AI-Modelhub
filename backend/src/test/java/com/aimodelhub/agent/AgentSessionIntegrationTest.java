package com.aimodelhub.agent;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Agent 会话接口的集成测试（不调用大模型）。
 *
 * <p>覆盖的是<b>权限与生命周期</b>这条最容易出事、又最不需要 LLM 的路径：
 * <ul>
 *   <li>会话创建 / 列表 / 删除</li>
 *   <li>跨用户读取、删除一律 404（不是 403——不泄露「这个 id 存在」）</li>
 *   <li>未登录 401</li>
 * </ul>
 * 对话内容本身依赖真实的 DeepSeek 服务，放在手工验收脚本里跑，
 * 不放进单元测试，避免 CI 因网络或额度失败。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Agent 会话接口：生命周期与用户隔离")
class AgentSessionIntegrationTest {

    private static final String PWD = "AgentIt#2026";
    private static final String PREFIX = "agentit_";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private String userA;
    private String userB;
    private String tokenA;
    private String tokenB;
    private Long sessionId;

    private String base() {
        return "http://localhost:" + port;
    }

    @BeforeAll
    void setUp() {
        userA = PREFIX + "a_" + suffix;
        userB = PREFIX + "b_" + suffix;
        register(userA);
        register(userB);
        tokenA = login(userA);
        tokenB = login(userB);
    }

    @AfterAll
    void cleanUp() {
        // 只清理本次测试自己造的数据（前缀 + 随机后缀），不触碰任何真实用户
        jdbcTemplate.update(
                "DELETE FROM agent_message WHERE user_id IN (SELECT id FROM sys_user WHERE username LIKE ?)",
                PREFIX + "%" + suffix);
        jdbcTemplate.update(
                "DELETE FROM agent_session WHERE user_id IN (SELECT id FROM sys_user WHERE username LIKE ?)",
                PREFIX + "%" + suffix);
        jdbcTemplate.update("DELETE FROM sys_user WHERE username LIKE ?", PREFIX + "%" + suffix);
    }

    @Test
    @Order(1)
    @DisplayName("未登录访问会话列表返回 401")
    void unauthenticated() {
        ResponseEntity<String> res = rest.getForEntity(base() + "/api/agent/sessions", String.class);
        assertEquals(401, res.getStatusCode().value());
    }

    @Test
    @Order(2)
    @DisplayName("创建会话：标题留空时落库为「新会话」")
    @SuppressWarnings("unchecked")
    void createSession() {
        ResponseEntity<Map> res = exchange("/api/agent/sessions", HttpMethod.POST, tokenA, Map.of());
        assertEquals(200, res.getStatusCode().value());
        Map<String, Object> data = (Map<String, Object>) res.getBody().get("data");
        sessionId = ((Number) data.get("id")).longValue();
        assertNotNull(sessionId);
        assertEquals("新会话", data.get("title"));
    }

    @Test
    @Order(3)
    @DisplayName("会话列表只返回自己的会话")
    @SuppressWarnings("unchecked")
    void listOnlyOwn() {
        List<Map<String, Object>> mine =
                (List<Map<String, Object>>) exchange("/api/agent/sessions", HttpMethod.GET, tokenA, null)
                        .getBody().get("data");
        assertTrue(mine.stream().anyMatch(s -> ((Number) s.get("id")).longValue() == sessionId));

        List<Map<String, Object>> others =
                (List<Map<String, Object>>) exchange("/api/agent/sessions", HttpMethod.GET, tokenB, null)
                        .getBody().get("data");
        assertTrue(others.stream().noneMatch(s -> ((Number) s.get("id")).longValue() == sessionId));
    }

    @Test
    @Order(4)
    @DisplayName("他人会话：读消息、删除都返回 404")
    void crossUserDenied() {
        assertEquals(404, exchange("/api/agent/sessions/" + sessionId + "/messages",
                HttpMethod.GET, tokenB, null).getStatusCode().value());
        assertEquals(404, exchange("/api/agent/sessions/" + sessionId,
                HttpMethod.DELETE, tokenB, null).getStatusCode().value());
    }

    @Test
    @Order(5)
    @DisplayName("删除后自己的会话也不再可见")
    @SuppressWarnings("unchecked")
    void deleteSession() {
        assertEquals(200, exchange("/api/agent/sessions/" + sessionId,
                HttpMethod.DELETE, tokenA, null).getStatusCode().value());

        assertEquals(404, exchange("/api/agent/sessions/" + sessionId + "/messages",
                HttpMethod.GET, tokenA, null).getStatusCode().value());

        List<Map<String, Object>> mine =
                (List<Map<String, Object>>) exchange("/api/agent/sessions", HttpMethod.GET, tokenA, null)
                        .getBody().get("data");
        assertTrue(mine.stream().noneMatch(s -> ((Number) s.get("id")).longValue() == sessionId));
    }

    // ── helpers ────────────────────────────────────────────────────

    private void register(String username) {
        Map<String, Object> body = Map.of(
                "username", username,
                "password", PWD,
                "nickname", "Agent集成测试",
                "securityQuestion", "q",
                "securityAnswer", "a");
        ResponseEntity<Map> res = rest.postForEntity(base() + "/api/auth/register", body, Map.class);
        assertEquals(200, res.getStatusCode().value(), "注册失败: " + res.getBody());
    }

    @SuppressWarnings("unchecked")
    private String login(String username) {
        ResponseEntity<Map> res = rest.postForEntity(base() + "/api/auth/login",
                Map.of("username", username, "password", PWD), Map.class);
        Map<String, Object> data = (Map<String, Object>) res.getBody().get("data");
        return String.valueOf(data.get("token"));
    }

    private ResponseEntity<Map> exchange(String path, HttpMethod method, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange(base() + path, method, new HttpEntity<>(body, headers), Map.class);
    }
}
