package com.aimodelhub.predict.dto;

import jakarta.validation.constraints.NotBlank;

public class TextPredictDTO {
    @NotBlank(message = "文本不能为空")
    public String text;

    public Long modelId;
}
