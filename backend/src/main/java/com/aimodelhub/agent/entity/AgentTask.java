package com.aimodelhub.agent.entity;

import java.time.LocalDateTime;

/**
 * Agent 推理任务（对应表 {@code agent_task}）。
 *
 * <h3>状态机</h3>
 * <pre>
 *   PENDING ──用户点「确认执行」──▶ CONFIRMED ──开始调用模型──▶ RUNNING ──▶ SUCCESS
 *      │                              │                          └────▶ FAILED
 *      ├──超时(10min)──▶ EXPIRED      └──用户取消──▶ CANCELLED
 *      └──用户取消──▶ CANCELLED
 * </pre>
 * 状态流转全部用「带条件的 UPDATE」（{@code WHERE task_status='PENDING'}）实现，
 * 靠数据库行锁保证：两个并发的确认请求只有一个能把状态从 PENDING 改成 CONFIRMED，
 * 另一个影响行数为 0 → 直接拒绝。这样「重复确认导致重复推理」在存储层就被挡住，
 * 不需要分布式锁。
 */
public class AgentTask {

    public static final String TYPE_INFERENCE = "INFERENCE";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_EXPIRED = "EXPIRED";

    private Long id;
    private String taskNo;
    private Long userId;
    private Long sessionId;
    private String taskType;
    private Long modelId;
    private String modelName;
    private String taskStatus;
    private String inputType;
    private String inputSummary;
    private String inputPayload;
    private Long attachmentId;
    private String resultSummary;
    private String resultPayload;
    private String errorMessage;
    private Long recordId;
    private Integer durationMs;
    private LocalDateTime confirmedAt;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public boolean isPending() {
        return STATUS_PENDING.equals(taskStatus);
    }

    public boolean isFinished() {
        return STATUS_SUCCESS.equals(taskStatus) || STATUS_FAILED.equals(taskStatus);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaskNo() {
        return taskNo;
    }

    public void setTaskNo(String taskNo) {
        this.taskNo = taskNo;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public Long getModelId() {
        return modelId;
    }

    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getTaskStatus() {
        return taskStatus;
    }

    public void setTaskStatus(String taskStatus) {
        this.taskStatus = taskStatus;
    }

    public String getInputType() {
        return inputType;
    }

    public void setInputType(String inputType) {
        this.inputType = inputType;
    }

    public String getInputSummary() {
        return inputSummary;
    }

    public void setInputSummary(String inputSummary) {
        this.inputSummary = inputSummary;
    }

    public String getInputPayload() {
        return inputPayload;
    }

    public void setInputPayload(String inputPayload) {
        this.inputPayload = inputPayload;
    }

    public Long getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(Long attachmentId) {
        this.attachmentId = attachmentId;
    }

    public String getResultSummary() {
        return resultSummary;
    }

    public void setResultSummary(String resultSummary) {
        this.resultSummary = resultSummary;
    }

    public String getResultPayload() {
        return resultPayload;
    }

    public void setResultPayload(String resultPayload) {
        this.resultPayload = resultPayload;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Integer durationMs) {
        this.durationMs = durationMs;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(LocalDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
