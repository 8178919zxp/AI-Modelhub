package com.aimodelhub.predict.vo;

import java.util.List;
import java.util.Map;

public class PredictResultVO {
    public String type;
    public String modelName;
    public String topLabel;
    public Double confidence;
    public Integer duration;
    public List<Map<String, Object>> candidates;
    public String resultType;
    public String imageBase64;
    public Double predictedClose;

    public PredictResultVO() {
    }

    public PredictResultVO(String type, String modelName, String topLabel, Double confidence,
                           Integer duration, List<Map<String, Object>> candidates) {
        this.type = type;
        this.modelName = modelName;
        this.topLabel = topLabel;
        this.confidence = confidence;
        this.duration = duration;
        this.candidates = candidates;
    }

    public String getImageBase64() { return imageBase64; }
    public void setImageBase64(String imageBase64) { this.imageBase64 = imageBase64; }

    public Double getPredictedClose() { return predictedClose; }
    public void setPredictedClose(Double predictedClose) { this.predictedClose = predictedClose; }
}
