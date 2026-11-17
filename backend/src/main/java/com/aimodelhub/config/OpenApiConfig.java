package com.aimodelhub.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3 / Swagger UI 配置。
 * <p>
 * Swagger UI 地址：<a href="http://localhost:8080/docs">/docs</a>
 * <br>
 * OpenAPI JSON 描述：<a href="http://localhost:8080/v3/api-docs">/v3/api-docs</a>
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private int serverPort;

    /** 构建 OpenAPI 文档的标题、版本、描述与服务器地址。 */
    @Bean
    public OpenAPI aiModelHubOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI ModelHub API")
                        .description("""
                                提供模型管理、预测服务、用户管理和数据面板的 RESTful API。

                                ## 认证
                                除公开接口外，所有 API 需要在请求头携带 JWT Token：
                                ```
                                Authorization: Bearer <token>
                                ```

                                ## 响应格式
                                统一响应体结构：
                                ```json
                                { "code": 200, "message": "success", "data": { ... } }
                                ```

                                ## RESTful 设计规范
                                - 资源使用名词复数（如 `/api/models`）
                                - 正确使用 HTTP 方法（GET/POST/PUT/PATCH/DELETE）
                                - 返回标准 HTTP 状态码
                                - 避免 URL 中使用动词
                                """)
                        .version("0.0.1")
                        .contact(new Contact()
                                .name("AI ModelHub Team"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("本地开发环境")
                ))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("BearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("输入 JWT Token（不含 'Bearer ' 前缀的系统会自动添加）")));
    }
}
