package com.aimodelhub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    /**
     * 配置调用模型服务用的 RestTemplate，设置连接与读取超时，避免请求被长时间挂起。
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
