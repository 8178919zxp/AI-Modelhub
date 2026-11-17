package com.aimodelhub.agent.vo;

import java.util.List;

/**
 * 回给前端（以及作为工具返回）的模型摘要。
 * <p>
 * 字段刻意收窄：工具返回会整体进入大模型上下文，塞进 cover 图片地址、
 * create_time 之类的噪声只会浪费 token 并干扰判断。
 */
public class ModelBriefVO {

    private Long id;
    private String name;
    private String type;
    private String typeLabel;
    private String algorithm;
    private Double accuracy;
    private Integer status;
    private String description;
    private String inputType;
    private List<String> requiredFields;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTypeLabel() {
        return typeLabel;
    }

    public void setTypeLabel(String typeLabel) {
        this.typeLabel = typeLabel;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public Double getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(Double accuracy) {
        this.accuracy = accuracy;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getInputType() {
        return inputType;
    }

    public void setInputType(String inputType) {
        this.inputType = inputType;
    }

    public List<String> getRequiredFields() {
        return requiredFields;
    }

    public void setRequiredFields(List<String> requiredFields) {
        this.requiredFields = requiredFields;
    }
}
