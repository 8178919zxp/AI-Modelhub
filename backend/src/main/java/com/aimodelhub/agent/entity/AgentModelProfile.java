package com.aimodelhub.agent.entity;

/**
 * 模型输入规范（对应表 {@code agent_model_profile}）。
 * <p>
 * 只描述「能不能跑、要什么输入」这类结构化事实，不复述 model_info 的名称与简介，
 * 避免同一事实存两处、将来不一致。
 */
public class AgentModelProfile {

    /** 输入类型：图片文件 */
    public static final String INPUT_IMAGE = "IMAGE";
    /** 输入类型：纯文本 */
    public static final String INPUT_TEXT = "TEXT";
    /** 输入类型：结构化数值特征 */
    public static final String INPUT_FEATURES = "FEATURES";

    private Long id;
    private Long modelId;
    private String inputType;
    private String requiredFields;
    private String exampleInput;
    private String usageNote;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getModelId() {
        return modelId;
    }

    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }

    public String getInputType() {
        return inputType;
    }

    public void setInputType(String inputType) {
        this.inputType = inputType;
    }

    public String getRequiredFields() {
        return requiredFields;
    }

    public void setRequiredFields(String requiredFields) {
        this.requiredFields = requiredFields;
    }

    public String getExampleInput() {
        return exampleInput;
    }

    public void setExampleInput(String exampleInput) {
        this.exampleInput = exampleInput;
    }

    public String getUsageNote() {
        return usageNote;
    }

    public void setUsageNote(String usageNote) {
        this.usageNote = usageNote;
    }
}
