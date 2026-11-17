package com.aimodelhub.predict.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface ModelGatewayService {
    Map<String, Object> predictText(String text, Long modelId);
    Map<String, Object> predictTabular(Map<String, Object> features, Long modelId);
    Map<String, Object> predictImage(MultipartFile file, String modelKey);

    /** 自定义模型预测（天气、股票、中文情感）。 */
    Map<String, Object> predictCustom(String modelKey, Map<String, Object> payload);
}
