package com.aimodelhub.predict.service.impl;

import com.aimodelhub.predict.service.ModelGatewayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Service
public class FastApiModelGatewayServiceImpl implements ModelGatewayService {

    private static final Logger log = LoggerFactory.getLogger(FastApiModelGatewayServiceImpl.class);

    private final RestTemplate restTemplate;

    @Value("${model-service.base-url:http://localhost:8001}")
    private String modelServiceBaseUrl;

    public FastApiModelGatewayServiceImpl(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /** 调用模型服务的文本分类接口，返回原始响应。 */
    @Override
    public Map<String, Object> predictText(String text, Long modelId) {
        String url = modelServiceBaseUrl + "/text/classify";
        Map<String, Object> payload = new HashMap<>();
        payload.put("text", text);
        payload.put("model_id", modelId);
        return restTemplate.postForObject(url, payload, Map.class);
    }

    /** 调用模型服务的表格预测接口，返回原始响应。 */
    @Override
    public Map<String, Object> predictTabular(Map<String, Object> features, Long modelId) {
        String url = modelServiceBaseUrl + "/tabular/predict";
        Map<String, Object> payload = new HashMap<>();
        payload.put("model_id", modelId);
        payload.put("features", features);
        return restTemplate.postForObject(url, payload, Map.class);
    }

    /**
     * 调用模型服务的图像预测接口。
     * 图片以 multipart 形式转发，模型 key 通过表单字段传递。
     */
    @Override
    public Map<String, Object> predictImage(MultipartFile file, String modelKey) {
        // 所有图像模型统一走 multipart 的 /predict/image 接口
        String url = modelServiceBaseUrl + "/predict/image";

        byte[] imageBytes;
        try {
            imageBytes = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("读取上传文件失败", e);
        }

        // 构造 multipart 表单数据
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("model_key", modelKey);

        ByteArrayResource fileResource = new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
            }
        };
        body.add("file", fileResource);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, Map.class);
        Map<String, Object> responseBody = response.getBody();
        log.debug("model-service /predict/image responded: status={}", response.getStatusCode());
        return responseBody;
    }

    /**
     * 调用自定义模型接口（天气 / 股票 / 中文情感），按 modelKey 由下游分派。
     */
    @Override
    public Map<String, Object> predictCustom(String modelKey, Map<String, Object> payload) {
        String url = modelServiceBaseUrl + "/predict/custom";

        Map<String, Object> body = new HashMap<>(payload);
        body.put("model_key", modelKey);

        ResponseEntity<Map> response = restTemplate.postForEntity(url, body, Map.class);
        Map<String, Object> responseBody = response.getBody();
        log.debug("model-service /predict/custom responded: modelKey={}, status={}",
                modelKey, response.getStatusCode());
        return responseBody;
    }
}
