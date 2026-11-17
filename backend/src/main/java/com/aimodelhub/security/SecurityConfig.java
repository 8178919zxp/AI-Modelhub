package com.aimodelhub.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // JWT 认证过滤器
    private final JwtAuthFilter jwtAuthFilter;
    // 跨域配置
    private final CorsConfigurationSource corsConfigurationSource;
    // 是否启用 API 文档

    /**
     * 仅当 springdoc 本身开启时才暴露 Swagger / OpenAPI
     * （开发环境为 true，生产环境为 false）。
     * 这样开发时能查看文档，生产环境又不会对外开放。
     */
    @Value("${springdoc.api-docs.enabled:false}")
    private boolean apiDocsEnabled;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          CorsConfigurationSource corsConfigurationSource) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    /**
     * 安全过滤链：关闭 CSRF、启用无状态会话、装配 JWT 过滤器，
     * 并按「公开接口 / 登录后可用 / 仅管理员」三档配置访问规则。
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 如果没有配置 entry point，Spring Security 对未登录请求会直接返回
            // 403 且响应体为空，前端无法区分「没登录」与「权限不足」。
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint((request, response, authException) ->
                            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                                    "未登录或登录已过期，请重新登录"))
                    .accessDeniedHandler((request, response, accessDeniedException) ->
                            writeJson(response, HttpServletResponse.SC_FORBIDDEN, "权限不足")))
            .authorizeHttpRequests(auth -> auth
                // ── 异步/错误派发 —— 必须放行 ────────────────────
                // SSE（Flux<ServerSentEvent>）在 Tomcat 上会产生一次 ASYNC 派发，
                // 派发线程里没有 SecurityContext，若这里不放行，
                // 过滤器链会二次鉴权并抛 AccessDeniedException，
                // 表现就是「流正常输出，但结束时连接异常中断 / 500」。
                // 放行是安全的：能走到 ASYNC/ERROR 派发，说明首次请求已经过鉴权。
                .dispatcherTypeMatchers(
                        jakarta.servlet.DispatcherType.ASYNC,
                        jakarta.servlet.DispatcherType.ERROR).permitAll()
                // 放行 CORS 预检请求
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // ── 基础设施端点 — 禁止外部访问 ──────────────────
                .requestMatchers("/actuator/**").denyAll()
                // Swagger UI —— 仅在 springdoc.api-docs.enabled=true（开发环境）时放行
                .requestMatchers("/v3/api-docs/**", "/docs", "/docs/**", "/swagger-ui/**")
                        .access((authentication, context) -> new AuthorizationDecision(apiDocsEnabled))
                // ── 公开端点 ──────────────────────────────────────
                .requestMatchers("/api/auth/login", "/api/auth/register",
                        "/api/auth/forgot-password/question",
                        "/api/auth/forgot-password/reset").permitAll()
                .requestMatchers("/api/health", "/api/health/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/models", "/api/models/**").permitAll()
                .requestMatchers("/api/dashboard/overview").permitAll()
                // 文件代理 —— 公开下载，无需认证
                .requestMatchers(HttpMethod.GET, "/api/files/**").permitAll()
                // ── 需认证端点 ────────────────────────────────────
                .requestMatchers("/api/predict/**").authenticated()
                // ModelHub Copilot：会话/消息/任务都绑定登录用户，
                // 统一要求认证；具体「是不是自己的数据」由 Service 层按 userId 过滤
                .requestMatchers("/api/agent/**").authenticated()
                .requestMatchers("/api/users/**").authenticated()
                // ── 管理员端点 ────────────────────────────────────
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // ── 兜底 — 其余全部需认证 ────────────────────────
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** 密码编码器：使用 BCrypt 对用户密码做哈希。 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 暴露 AuthenticationManager，供登录接口校验账号密码。 */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /** 输出与其他接口一致的 {code,message} 响应结构。 */
    private static void writeJson(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + status + ",\"message\":\"" + message + "\",\"data\":null}");
    }
}
