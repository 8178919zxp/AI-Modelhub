package com.aimodelhub.agent.service;

import com.aimodelhub.agent.entity.AgentTask;
import com.aimodelhub.agent.mapper.AgentTaskMapper;
import com.aimodelhub.common.exception.BusinessException;
import com.aimodelhub.common.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 推理任务的持久化与状态机。
 *
 * <h3>为什么「确认」必须走数据库条件更新，而不是内存标记</h3>
 * 用户可能双击确认按钮、前端可能重试、多实例部署时请求可能落到不同节点。
 * 只要状态迁移是「{@code UPDATE ... WHERE task_status='PENDING'}」并检查影响行数，
 * 数据库行锁天然保证只有一个请求能成功。用 JVM 内存标记则多实例直接失效。
 *
 * <h3>过期策略</h3>
 * 待确认任务 10 分钟内未确认即视为过期。过期的任务<b>不会被物理删除</b>——
 * 保留下来才能回答「用户点了准备为什么没执行」这类问题。
 */
@Service
public class AgentTaskService {

    private static final Logger log = LoggerFactory.getLogger(AgentTaskService.class);

    /** 待确认任务的有效期 */
    private static final Duration PENDING_TTL = Duration.ofMinutes(10);

    private static final DateTimeFormatter NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AgentTaskMapper taskMapper;

    public AgentTaskService(AgentTaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    /** 创建待确认任务。 */
    public AgentTask create(Long userId, Long sessionId, Long modelId, String modelName,
                            String inputType, String inputSummary, String inputPayload, Long attachmentId) {
        AgentTask task = new AgentTask();
        task.setTaskNo(newTaskNo());
        task.setUserId(userId);
        task.setSessionId(sessionId);
        task.setTaskType(AgentTask.TYPE_INFERENCE);
        task.setModelId(modelId);
        task.setModelName(modelName);
        task.setTaskStatus(AgentTask.STATUS_PENDING);
        task.setInputType(inputType);
        task.setInputSummary(inputSummary);
        task.setInputPayload(inputPayload);
        task.setAttachmentId(attachmentId);
        taskMapper.insert(task);
        log.info("Agent 任务已创建: id={}, no={}, userId={}, model={}",
                task.getId(), task.getTaskNo(), userId, modelName);
        return task;
    }

    /**
     * 取任务并校验归属；顺带做过期判定。
     * 别人的任务与不存在的任务统一 404，避免「id 是否存在」被探测。
     */
    public AgentTask requireOwned(Long userId, Long taskId) {
        AgentTask task = taskMapper.findByIdAndUser(taskId, userId);
        if (task == null) {
            throw new NotFoundException("任务不存在");
        }
        if (AgentTask.STATUS_PENDING.equals(task.getTaskStatus())
                && task.getCreateTime() != null
                && task.getCreateTime().plus(PENDING_TTL).isBefore(LocalDateTime.now())) {
            taskMapper.markExpired(taskId);
            task.setTaskStatus(AgentTask.STATUS_EXPIRED);
            throw new BusinessException("任务已过期（超过 " + PENDING_TTL.toMinutes() + " 分钟未确认），请重新发起");
        }
        return task;
    }

    /**
     * PENDING → CONFIRMED。
     *
     * @throws BusinessException 状态已被改变（重复确认 / 已执行 / 已取消 / 已过期）
     */
    public void confirm(Long userId, AgentTask task) {
        int affected = taskMapper.markConfirmed(task.getId(), userId);
        if (affected == 0) {
            AgentTask latest = taskMapper.findByIdAndUser(task.getId(), userId);
            String status = latest == null ? "UNKNOWN" : latest.getTaskStatus();
            throw new BusinessException(switch (status) {
                case AgentTask.STATUS_CONFIRMED, AgentTask.STATUS_RUNNING, AgentTask.STATUS_SUCCESS ->
                        "该任务已确认或已执行，无需重复确认";
                case AgentTask.STATUS_EXPIRED, AgentTask.STATUS_CANCELLED ->
                        "任务已" + ("EXPIRED".equals(status) ? "过期" : "取消") + "，请重新发起";
                default -> "任务当前状态为 " + status + "，无法确认";
            });
        }
        task.setTaskStatus(AgentTask.STATUS_CONFIRMED);
    }

    /**
     * CONFIRMED → RUNNING（抢执行权）。
     *
     * @return true 表示本次抢到执行权；false 表示已被执行或状态不符
     */
    public boolean claimForRun(Long userId, Long taskId) {
        return taskMapper.markRunning(taskId, userId) == 1;
    }

    public void markSuccess(Long taskId, String resultSummary, String resultPayload,
                            Long recordId, Integer durationMs) {
        taskMapper.markSuccess(taskId, truncate(resultSummary, 500), resultPayload, recordId, durationMs);
    }

    public void markFailed(Long taskId, String errorMessage, Integer durationMs) {
        taskMapper.markFailed(taskId, truncate(errorMessage, 500), durationMs);
    }

    public List<AgentTask> listRecent(Long userId, int limit) {
        return taskMapper.findByUser(userId, Math.min(Math.max(limit, 1), 50));
    }

    public AgentTask findByIdAndUser(Long userId, Long taskId) {
        return taskMapper.findByIdAndUser(taskId, userId);
    }

    /** 业务号：T + 时间戳 + 4 位随机，便于人工沟通时引用而不暴露自增规模。 */
    private static String newTaskNo() {
        return "T" + LocalDateTime.now().format(NO_FORMAT) + String.format("%04d", RANDOM.nextInt(10000));
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
