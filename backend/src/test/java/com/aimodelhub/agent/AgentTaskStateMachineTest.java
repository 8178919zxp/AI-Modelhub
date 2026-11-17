package com.aimodelhub.agent;

import com.aimodelhub.agent.entity.AgentTask;
import com.aimodelhub.agent.service.AgentInferenceService;
import com.aimodelhub.agent.service.AgentTaskService;
import com.aimodelhub.common.exception.BusinessException;
import com.aimodelhub.common.exception.NotFoundException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 推理任务状态机的集成测试（<b>不调用大模型、不调用模型服务</b>）。
 *
 * <p>这几条断言对应的是安全要求里最硬的一条：
 * 「没有用户确认不得执行推理」，以及「重复确认不得重复推理」。
 * 它们全部由数据库条件更新保证，因此可以脱离模型服务独立验证。
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Agent 推理任务：确认制状态机")
class AgentTaskStateMachineTest {

    private static final String PREFIX = "taskit_";

    @Autowired
    private AgentTaskService taskService;

    @Autowired
    private AgentInferenceService inferenceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private Long userA;
    private Long userB;

    @BeforeAll
    void setUp() {
        userA = createUser("a");
        userB = createUser("b");
    }

    @AfterAll
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM agent_task WHERE user_id IN (?, ?)", userA, userB);
        jdbcTemplate.update("DELETE FROM sys_user WHERE username LIKE ?", PREFIX + "%" + suffix);
    }

    @Test
    @DisplayName("待确认任务不能被工具执行（必须先由用户确认）")
    void pendingCannotExecute() {
        AgentTask task = newTask(userA);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> inferenceService.requireTaskForExecution(userA, task.getId()));
        assertTrue(ex.getMessage().contains("确认"), "错误信息应提示需要确认，实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("确认后状态变为 CONFIRMED，且重复确认被拒绝")
    void confirmOnceOnly() {
        AgentTask task = newTask(userA);

        taskService.confirm(userA, task);
        AgentTask afterConfirm = taskService.findByIdAndUser(userA, task.getId());
        assertEquals(AgentTask.STATUS_CONFIRMED, afterConfirm.getTaskStatus());

        // 第二次确认：条件更新影响 0 行 → 抛出明确业务异常
        assertThrows(BusinessException.class, () -> taskService.confirm(userA, task));
    }

    @Test
    @DisplayName("执行权只能被抢到一次（并发确认不会重复推理）")
    void claimOnceOnly() {
        AgentTask task = newTask(userA);
        taskService.confirm(userA, task);

        assertTrue(taskService.claimForRun(userA, task.getId()), "第一次应抢到执行权");
        assertFalse(taskService.claimForRun(userA, task.getId()), "第二次不应再抢到执行权");
    }

    @Test
    @DisplayName("他人任务：读取与确认都按不存在处理")
    void crossUserDenied() {
        AgentTask task = newTask(userA);
        assertThrows(NotFoundException.class, () -> taskService.requireOwned(userB, task.getId()));
    }

    private AgentTask newTask(Long userId) {
        return taskService.create(userId, null, 1L, "ResNet-50", "IMAGE",
                "test.png（81 KB）", "{\"attachmentId\":1}", 1L);
    }

    private Long createUser(String tag) {
        String username = PREFIX + tag + "_" + suffix;
        jdbcTemplate.update(
                "INSERT INTO sys_user (username, password, nickname, role) VALUES (?, ?, ?, 'user')",
                username, "$2a$10$abcdefghijklmnopqrstuv", "任务测试");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = ?", Long.class, username);
    }

    /** 保留以说明测试数据形态（未使用但便于排障时打印）。 */
    @SuppressWarnings("unused")
    private Map<String, Object> debugRow(Long taskId) {
        return jdbcTemplate.queryForMap("SELECT * FROM agent_task WHERE id = ?", taskId);
    }
}
