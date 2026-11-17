package com.aimodelhub.model.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新增与更新模型共用的数据传输对象。
 * <p>
 * 与 {@link com.aimodelhub.model.entity.ModelInfo} 的字段映射：
 * <pre>
 *   modelName  → model_name  （JSON 字段名："name"）
 *   modelType  → model_type  （JSON 字段名："type"）
 *   algorithm  → algorithm
 *   accuracy   → accuracy    （0–100）
 *   description → description
 *   cover      → cover       （可选，URL 字符串）
 *   modelApi   → model_api   （JSON 字段名："api"）
 *   status     → status      （1=上线，0=下线；默认 1）
 * </pre>
 */
public class ModelSaveDTO {

    @NotBlank(message = "模型名称不能为空")
    @Size(max = 100, message = "模型名称最多100个字符")
    public String modelName;

    @NotBlank(message = "模型类型不能为空")
    @Size(max = 50, message = "模型类型最多50个字符")
    public String modelType;

    @Size(max = 150, message = "算法名称最多150个字符")
    public String algorithm;

    @DecimalMin(value = "0", message = "准确率不能小于0")
    @DecimalMax(value = "100", message = "准确率不能大于100")
    public Double accuracy;
    // 描述
    public String description;

    @Size(max = 255, message = "封面URL最多255个字符")
    public String cover;

    @Size(max = 255, message = "接口地址最多255个字符")
    public String modelApi;

    /** 1=上线，0=下线。不传时默认 1（上线）。 */
    public Integer status;

    public ModelSaveDTO() {
    }
}
