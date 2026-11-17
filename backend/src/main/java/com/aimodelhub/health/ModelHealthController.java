package com.aimodelhub.health;

import com.aimodelhub.common.api.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class ModelHealthController {

    private final RestTemplate restTemplate;

    @Value("${model-service.base-url:http://localhost:8001}")
    private String modelServiceBaseUrl;

    public ModelHealthController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /** 检查模型服务（FastAPI）是否可用，供前端展示服务状态。 */
    @GetMapping("/models")
    public Result<Map<String, Object>> modelHealth() {
        try {
            String url = modelServiceBaseUrl + "/health";
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.getForObject(url, Map.class);
            return Result.ok(resp);
        } catch (Exception e) {
            return Result.ok(Map.of(
                    "status", "DOWN",
                    "error", "Model service unreachable: " + e.getMessage(),
                    "loaded", 0,
                    "failed", 4,
                    "models", Map.of()
            ));
        }
    }
}
