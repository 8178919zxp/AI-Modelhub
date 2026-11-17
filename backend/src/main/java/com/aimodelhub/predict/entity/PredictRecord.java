package com.aimodelhub.predict.entity;

import java.time.LocalDateTime;

/**
 * 一条预测记录。通过 {@code PredictRecordMapper} 持久化，
 * 列名在 {@code mapper/PredictRecordMapper.xml} 中显式映射。
 */
public class PredictRecord {

    private Long id;

    private Long userId;

    private Long modelId;

    private String fileUrl;

    private String originalFilename;

    private String inputText;

    private String predictResult;

    private Double confidence;

    private Integer duration;

    private LocalDateTime createTime;

    public PredictRecord() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getModelId() { return modelId; }
    public void setModelId(Long modelId) { this.modelId = modelId; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }

    public String getInputText() { return inputText; }
    public void setInputText(String inputText) { this.inputText = inputText; }

    public String getPredictResult() { return predictResult; }
    public void setPredictResult(String predictResult) { this.predictResult = predictResult; }

    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }

    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    /** 插入前由 Service 调用，填充 createTime。 */
    public void onCreate() {
        if (this.createTime == null) {
            this.createTime = LocalDateTime.now();
        }
    }
}
