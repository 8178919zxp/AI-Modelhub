package com.aimodelhub.predict.dto;

import java.util.Map;

/** 自定义模型预测的请求体（天气、股票、中文情感）。 */
public class CustomPredictDTO {
    public Long modelId;
    public String text;
    public Map<String, Object> features;
}
