package com.aimodelhub.agent.vo;

import java.util.Map;

/**
 * 推理任务执行结果（确认接口与推理工具共用）。
 * <p>
 * 刻意不返回 {@code imageBase64}：U2Net 抠图结果可能几百 KB，
 * 塞进 JSON 既拖慢响应也会被前端误当成图片地址。图片结果请走
 * {@code /api/users/records} 里记录的 fileUrl。
 */
public class AgentTaskResultVO {

    private Long taskId;
    private String taskNo;
    private String status;
    private Long modelId;
    private String modelName;
    private String topLabel;
    private Double confidence;
    private Integer durationMs;
    private Map<String, Object> candidates;
    private String explanation;
    private Long recordId;
    /** 状态不是 SUCCESS 时的说明（例如「需要用户确认」） */
    private String message;

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getTaskNo() {
        return taskNo;
    }

    public void setTaskNo(String taskNo) {
        this.taskNo = taskNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public String getTopLabel() {
        return topLabel;
    }

    public void setTopLabel(String topLabel) {
        this.topLabel = topLabel;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Integer durationMs) {
        this.durationMs = durationMs;
    }

    public Map<String, Object> getCandidates() {
        return candidates;
    }

    public void setCandidates(Map<String, Object> candidates) {
        this.candidates = candidates;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
