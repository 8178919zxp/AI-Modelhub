package com.aimodelhub.agent.vo;

import com.aimodelhub.agent.entity.AgentTask;

/**
 * 任务详情（{@code GET /api/agent/tasks/{id}} 与会话内的任务卡片）。
 */
public class AgentTaskVO {

    private Long taskId;
    private String taskNo;
    private String status;
    private Long modelId;
    private String modelName;
    private String inputType;
    private String inputSummary;
    private String resultSummary;
    private String errorMessage;
    private Long recordId;
    private String createTime;

    public static AgentTaskVO from(AgentTask task) {
        AgentTaskVO vo = new AgentTaskVO();
        vo.taskId = task.getId();
        vo.taskNo = task.getTaskNo();
        vo.status = task.getTaskStatus();
        vo.modelId = task.getModelId();
        vo.modelName = task.getModelName();
        vo.inputType = task.getInputType();
        vo.inputSummary = task.getInputSummary();
        vo.resultSummary = task.getResultSummary();
        vo.errorMessage = task.getErrorMessage();
        vo.recordId = task.getRecordId();
        vo.createTime = task.getCreateTime() == null ? null : task.getCreateTime().toString();
        return vo;
    }

    public Long getTaskId() {
        return taskId;
    }

    public String getTaskNo() {
        return taskNo;
    }

    public String getStatus() {
        return status;
    }

    public Long getModelId() {
        return modelId;
    }

    public String getModelName() {
        return modelName;
    }

    public String getInputType() {
        return inputType;
    }

    public String getInputSummary() {
        return inputSummary;
    }

    public String getResultSummary() {
        return resultSummary;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Long getRecordId() {
        return recordId;
    }

    public String getCreateTime() {
        return createTime;
    }
}
