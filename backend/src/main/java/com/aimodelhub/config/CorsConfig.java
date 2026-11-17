package com.aimodelhub.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    /** 未配置或配置为空时的默认值：与改造前硬编码的内容一致。 */
    private static final List<String> DEFAULT_ORIGINS = List.of(
            "http://localhost:5173",
            "http://127.0.0.1:5173",
            "http://localhost:4173"
    );

    /**
     * 允许的 Origin，逗号分隔。
     *
     * <p>原来这里是硬编码的三个地址，yml 里的 {@code app.cors.allowed-origins} 根本没被读取——
     * 换端口或换域名部署时只能改代码重新打包。现在改成读配置，并保留同样的默认值，
     * 不配置时行为与之前一致；部署时用环境变量 {@code APP_CORS_ALLOWED_ORIGINS} 覆盖即可。
     *
     * <p>不接受通配符 {@code *}：{@code allowCredentials=true} 时通配符本身也不被浏览器允许。
     */
    @Value("${app.cors.allowed-origins:}")
    private String allowedOrigins;

    /**
     * 配置跨域规则：按配置放行来源，并放行常用请求头与方法。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> origins = Arrays.stream(allowedOrigins == null ? new String[0] : allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        if (origins.isEmpty()) {
            // 空值（比如 compose 传了空字符串）按默认值处理，避免把开发环境配成「全部拒绝」
            origins = DEFAULT_ORIGINS;
        }

        if (origins.stream().anyMatch(o -> o.contains("*"))) {
            // 通配符 + 凭证会同时被浏览器和 Spring 拒绝，配置阶段就直接报出来，避免上线才踩
            throw new IllegalStateException(
                    "app.cors.allowed-origins 不允许使用通配符（allowCredentials=true）");
        }
        log.info("CORS 允许的 Origin: {}", origins);

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
