package com.aimodelhub.predict.service;

import com.aimodelhub.predict.vo.PredictResultVO;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PredictResultMapper {

    private PredictResultMapper() {
    }

    /**
     * 把模型服务的响应归一化成统一的 {@link PredictResultVO}。
     * 兼容新版 /predict、旧版 /image/classify 以及自定义模型三种响应结构。
     */
    @SuppressWarnings("unchecked")
    public static PredictResultVO fromMap(Map<String, Object> source) {
        // ── U²-Net 新格式 ─────────────────────────────────────
        // FastAPI 返回：resultType=image_processing、imageBase64、modelName=U2Net、duration、candidates
        if ("image_processing".equals(String.valueOf(source.get("resultType")))
                && source.containsKey("imageBase64")) {
            PredictResultVO vo = new PredictResultVO();
            vo.type = "image";
            vo.modelName = String.valueOf(source.getOrDefault("modelName", "U2Net"));
            vo.resultType = "image_processing";
            vo.imageBase64 = String.valueOf(source.get("imageBase64"));
            vo.topLabel = "background_removed";
            vo.confidence = 1.0;
            vo.duration = parseInt(source.getOrDefault("duration", 0));
            vo.candidates = Collections.emptyList();
            return vo;
        }

        // ── U²-Net 旧格式（兼容历史响应） ────────────────────
        // FastAPI 返回：type=image、result（base64）、cost_ms
        if ("image".equals(String.valueOf(source.get("type")))
                && source.containsKey("result")
                && !source.containsKey("candidates")
                && !source.containsKey("results")
                && !source.containsKey("top_label")) {
            PredictResultVO vo = new PredictResultVO();
            vo.type = "image";
            vo.modelName = "U2Net";
            vo.resultType = "image_processing";
            vo.imageBase64 = String.valueOf(source.get("result"));
            vo.topLabel = "background_removed";
            vo.confidence = 1.0;
            vo.duration = parseInt(source.getOrDefault("cost_ms",
                    source.getOrDefault("duration", 0)));
            vo.candidates = Collections.emptyList();
            return vo;
        }

        // model_name 是两种响应格式都有的字段
        String modelName = String.valueOf(source.getOrDefault("model_name", ""));

        // 由 model_name 反推 type，保持与旧接口兼容
        String type = _modelType(modelName);

        List<Map<String, Object>> candidates;
        String topLabel;
        Double confidence;
        Integer duration;

        if (source.containsKey("results")) {
            // 新版 /predict 响应：model_name、results[{label, probability}]、duration_ms、timestamp
            List<Map<String, Object>> results =
                    (List<Map<String, Object>>) (Object) _nullableDefault(source.get("results"), Collections.emptyList());
            candidates = results;
            if (!results.isEmpty()) {
                Map<String, Object> top = results.get(0);
                topLabel = String.valueOf(top.getOrDefault("label", "unknown"));
                confidence = parseDouble(top.getOrDefault("probability", 0));
            } else {
                topLabel = "unknown";
                confidence = 0D;
            }
            duration = parseInt(source.getOrDefault("duration_ms", 0));
        } else {
            // 旧版 /image/classify 响应：type、model_name、top_label、confidence、duration、candidates
            topLabel = String.valueOf(source.getOrDefault("top_label",
                    source.getOrDefault("topLabel", "unknown")));
            confidence = parseDouble(source.getOrDefault("confidence", 0));
            candidates = (List<Map<String, Object>>) (Object) _nullableDefault(source.get("candidates"), Collections.emptyList());
            duration = parseInt(source.getOrDefault("duration", 0));
        }

        PredictResultVO vo = new PredictResultVO(type, modelName, topLabel, confidence, duration, candidates);

        // 股票模型：存在 predicted_close 时原样透传
        if (source.containsKey("predicted_close")) {
            vo.predictedClose = parseDouble(source.get("predicted_close"));
        }

        return vo;
    }

    private static String _modelType(String modelName) {
        if (modelName == null) return "unknown";
        return switch (modelName) {
            case "ResNet-50" -> "image";
            case "YOLOv8" -> "image";
            case "BERT-Base" -> "text";
            case "XGBoost" -> "tabular";

            // 自定义模型
            case "ChestCT" -> "image";
            case "WeatherGBM" -> "tabular";
            case "StockRegression" -> "tabular";
            case "FlowerCNN" -> "image";
            case "ChineseSentiment" -> "text";
            case "中文情感（关键词规则）" -> "text";
            case "英文情感（关键词规则）" -> "text";

            default -> "unknown";
        };
    }

    private static Double parseDouble(Object value) {
        return value == null ? 0D : Double.parseDouble(String.valueOf(value));
    }

    private static Integer parseInt(Object value) {
        return value == null ? 0 : Integer.parseInt(String.valueOf(value));
    }

    /** 类似 getOrDefault，但当 key 存在而值为 null 时同样走默认值。 */
    private static <T> T _nullableDefault(Object value, T fallback) {
        return value != null ? (T) value : fallback;
    }
}
