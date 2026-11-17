package com.aimodelhub.model;

import com.aimodelhub.auth.dto.LoginDTO;
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
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ModelService CRUD, cache eviction, and admin auth.
 * <p>
 * <b>Prerequisites:</b>
 * <ul>
 *   <li>MySQL running at {@code localhost:3306} (database {@code ai_modelhub})</li>
 *   <li>Redis running at {@code localhost:6379}</li>
 * </ul>
 * 管理员与普通用户账号由 {@link IntegrationTestUsers} 在运行时创建、结束后删除，
 * 不再依赖可能被改动过的种子账号。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@DisplayName("ModelService CRUD + cache eviction integration tests")
class ModelServiceIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String baseUrl;

    private SysUser adminUser;
    private SysUser normalUser;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        adminUser = IntegrationTestUsers.create(sysUserMapper, passwordEncoder, "it_admin", "admin");
        normalUser = IntegrationTestUsers.create(sysUserMapper, passwordEncoder, "it_user", "user");
    }

    @AfterEach
    void tearDown() {
        IntegrationTestUsers.delete(jdbcTemplate, adminUser != null ? adminUser.getId() : null);
        IntegrationTestUsers.delete(jdbcTemplate, normalUser != null ? normalUser.getId() : null);
        // 本类会真实写入 model_info（新增/改名/软删除）。软删除只是把 status 置 -1，
        // 行仍留在表里——跑几十次测试就会堆出几十条 IntegrationTestModel_*。
        // 这里按名称前缀物理删除，保证测试不污染业务库。
        jdbcTemplate.update("""
                DELETE FROM model_info
                WHERE model_name LIKE 'IntegrationTestModel\\_%'
                   OR model_name LIKE 'UpdateTest\\_%'
                   OR model_name LIKE 'DeleteTest\\_%'
                """);
    }

    // ── helpers ────────────────────────────────────────────────────

    private String login(String username, String password) {
        LoginDTO dto = new LoginDTO();
        dto.username = username;
        dto.password = password;
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                baseUrl + "/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(dto, h), new ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
        return (String) data.get("token");
    }

    private <T> ResponseEntity<Map<String, Object>> adminPost(String path, Object body, String token) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(token);
        return restTemplate.exchange(baseUrl + path, HttpMethod.POST,
                new HttpEntity<>(body, h), new ParameterizedTypeReference<>() {});
    }

    private <T> ResponseEntity<Map<String, Object>> adminPut(String path, Object body, String token) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(token);
        return restTemplate.exchange(baseUrl + path, HttpMethod.PUT,
                new HttpEntity<>(body, h), new ParameterizedTypeReference<>() {});
    }

    private ResponseEntity<Map<String, Object>> adminDelete(String path, String token) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(token);
        return restTemplate.exchange(baseUrl + path, HttpMethod.DELETE,
                new HttpEntity<>(h), new ParameterizedTypeReference<>() {});
    }

    private ResponseEntity<Map<String, Object>> publicGet(String path) {
        return restTemplate.exchange(baseUrl + path, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});
    }

    private ResponseEntity<Map<String, Object>> authenticatedGet(String path, String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return restTemplate.exchange(baseUrl + path, HttpMethod.GET,
                new HttpEntity<>(h), new ParameterizedTypeReference<>() {});
    }

    // ────────────────────────────────────────────────────────────────
    // Test 1: Create model → cache eviction → public list refreshed
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Create model → public list includes it (cache evicted)")
    void createModelEvictsListCache() {
        String adminToken = login(adminUser.getUsername(), IntegrationTestUsers.PASSWORD);

        // Create a test model
        Map<String, Object> payload = Map.of(
                "modelName", "IntegrationTestModel_" + System.currentTimeMillis(),
                "modelType", "image",
                "algorithm", "CNN",
                "accuracy", 85.5,
                "status", 1
        );
        ResponseEntity<Map<String, Object>> createResp = adminPost("/api/admin/models", payload, adminToken);
        assertEquals(HttpStatus.OK, createResp.getStatusCode());
        assertEquals(200, createResp.getBody().get("code"));
        @SuppressWarnings("unchecked")
        Map<String, Object> created = (Map<String, Object>) createResp.getBody().get("data");
        assertNotNull(created.get("id"));
        String modelName = (String) created.get("name");

        // Public list should include the new model (cache was evicted by createModel)
        ResponseEntity<Map<String, Object>> listResp = publicGet("/api/models");
        assertEquals(HttpStatus.OK, listResp.getStatusCode());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> models = (List<Map<String, Object>>) listResp.getBody().get("data");
        boolean found = models.stream().anyMatch(m -> modelName.equals(m.get("name")));
        assertTrue(found, "Newly created model '" + modelName + "' should appear in public list");

        // Cleanup: delete the test model
        adminDelete("/api/admin/models/" + created.get("id"), adminToken);
    }

    // ────────────────────────────────────────────────────────────────
    // Test 2: Update model → detail cache evicted → detail updated
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Update model → detail endpoint returns updated fields")
    void updateModelEvictsDetailCache() {
        String adminToken = login(adminUser.getUsername(), IntegrationTestUsers.PASSWORD);

        // Create a test model
        Map<String, Object> payload = Map.of(
                "modelName", "UpdateTest_" + System.currentTimeMillis(),
                "modelType", "text",
                "algorithm", "Transformer",
                "accuracy", 90.0,
                "status", 1
        );
        var createResp = adminPost("/api/admin/models", payload, adminToken);
        @SuppressWarnings("unchecked")
        Map<String, Object> created = (Map<String, Object>) createResp.getBody().get("data");
        Long id = Long.valueOf(created.get("id").toString());

        // Update the model
        Map<String, Object> updatePayload = Map.of(
                "modelName", "UpdateTest_" + System.currentTimeMillis() + "_MODIFIED",
                "modelType", "text",
                "algorithm", "Transformer v2",
                "accuracy", 95.0,
                "status", 1
        );
        var updateResp = adminPut("/api/admin/models/" + id, updatePayload, adminToken);
        assertEquals(HttpStatus.OK, updateResp.getStatusCode());

        // Verify detail reflects update
        var detailResp = publicGet("/api/models/" + id);
        assertEquals(HttpStatus.OK, detailResp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> detail = (Map<String, Object>) detailResp.getBody().get("data");
        assertEquals(updatePayload.get("modelName"), detail.get("name"));
        assertEquals(updatePayload.get("algorithm"), detail.get("algorithm"));

        // Cleanup
        adminDelete("/api/admin/models/" + id, adminToken);
    }

    // ────────────────────────────────────────────────────────────────
    // Test 3: Soft-delete → public list excludes it → admin still sees it
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Soft-delete model → public list excludes it → 404 for detail")
    void softDeleteHidesFromPublicButAdminSeesIt() {
        String adminToken = login(adminUser.getUsername(), IntegrationTestUsers.PASSWORD);

        // Create then delete a test model
        Map<String, Object> payload = Map.of(
                "modelName", "DeleteTest_" + System.currentTimeMillis(),
                "modelType", "ml",
                "algorithm", "XGBoost",
                "accuracy", 80.0,
                "status", 1
        );
        var createResp = adminPost("/api/admin/models", payload, adminToken);
        @SuppressWarnings("unchecked")
        Map<String, Object> created = (Map<String, Object>) createResp.getBody().get("data");
        Long id = Long.valueOf(created.get("id").toString());

        // Soft-delete
        var deleteResp = adminDelete("/api/admin/models/" + id, adminToken);
        assertEquals(HttpStatus.OK, deleteResp.getStatusCode());

        // Public list should NOT include the deleted model
        var listResp = publicGet("/api/models");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> models = (List<Map<String, Object>>) listResp.getBody().get("data");
        boolean found = models.stream().anyMatch(m -> id.equals(Long.valueOf(m.get("id").toString())));
        assertFalse(found, "Deleted model should NOT appear in public list");

        // Public detail should return 404 for deleted model
        var detailResp = publicGet("/api/models/" + id);
        assertEquals(HttpStatus.NOT_FOUND, detailResp.getStatusCode());

        // Admin endpoint still returns it
        var adminListResp = authenticatedGet("/api/admin/models", adminToken);
        assertEquals(HttpStatus.OK, adminListResp.getStatusCode());
    }

    // ────────────────────────────────────────────────────────────────
    // Test 4: Non-admin user is denied access to admin endpoints
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Non-admin user gets 403 when accessing /api/admin/**")
    void nonAdminUserDeniedAdminEndpoints() {
        String userToken = login(normalUser.getUsername(), IntegrationTestUsers.PASSWORD);

        // Try to list models as non-admin → 403
        var resp = authenticatedGet("/api/admin/models", userToken);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(),
                "Non-admin user should get 403 on admin endpoints");

        // Try to create model as non-admin → 403
        Map<String, Object> payload = Map.of(
                "modelName", "UnauthorizedCreate",
                "modelType", "image"
        );
        var createResp = adminPost("/api/admin/models", payload, userToken);
        assertEquals(HttpStatus.FORBIDDEN, createResp.getStatusCode());
    }
}
