package com.aimodelhub.model.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

/**
 * 模型元数据。通过 {@code ModelInfoMapper} 持久化，
 * 列名在 {@code mapper/ModelInfoMapper.xml} 中显式映射。
 */
public class ModelInfo {

    private Long id;

    @JsonProperty("name")
    private String modelName;

    @JsonProperty("type")
    private String modelType;

    private String algorithm;

    private Double accuracy;

    private String description;

    private String cover;

    @JsonProperty("api")
    private String modelApi;

    private Integer status = 1;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public ModelInfo() {
    }

    // Getter 与 Setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getModelType() { return modelType; }
    public void setModelType(String modelType) { this.modelType = modelType; }

    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }

    public Double getAccuracy() { return accuracy; }
    public void setAccuracy(Double accuracy) { this.accuracy = accuracy; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCover() { return cover; }
    public void setCover(String cover) { this.cover = cover; }

    public String getModelApi() { return modelApi; }
    public void setModelApi(String modelApi) { this.modelApi = modelApi; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    // ---- 生命周期回调（由 Service 显式调用；原先对应 JPA 的 @PrePersist / @PreUpdate） ----

    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createTime == null) {
            this.createTime = now;
        }
        if (this.updateTime == null) {
            this.updateTime = now;
        }
    }

    /** 更新前由 Service 调用，刷新 updateTime。 */
    public void onUpdate() {
        this.updateTime = LocalDateTime.now();
    }
}
