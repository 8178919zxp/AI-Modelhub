package com.aimodelhub.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Date;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtUtil jwtUtil;
    private final TokenBlacklistService blacklistService;

    public JwtAuthFilter(JwtUtil jwtUtil, TokenBlacklistService blacklistService) {
        this.jwtUtil = jwtUtil;
        this.blacklistService = blacklistService;
    }

    @Override
    /**
     * 每个请求执行一次：解析 Bearer 令牌 → 校验签名与有效期 → 检查黑名单与改密吊销 → 写入认证上下文。
     * 校验失败时只清空上下文，不直接返回错误，交由后续的鉴权入口处理。
     */
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);
        String requestUri = request.getRequestURI();

        // ── 诊断日志：记录每个通过认证的请求 ────────────────────────────
        if (log.isDebugEnabled()) {
            Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();
            log.debug("[JWT] URI={} | existing-auth={} | has-token={}",
                    requestUri,
                    existingAuth != null ? existingAuth.getClass().getSimpleName() : "null",
                    StringUtils.hasText(token));
        }

        if (!StringUtils.hasText(token)) {
            // 没有携带令牌——预测相关接口用 INFO 级别记录，便于排查问题
            if (requestUri.startsWith("/api/predict/")) {
                log.info("[JWT] ⛔ NO TOKEN — URI={} — 预测接口需要认证，但 Authorization header 缺失或格式错误", requestUri);
            }
            filterChain.doFilter(request, response);
            return;
        }

        // ── 校验令牌（失败时给出具体原因） ─────────────────────────────
        if (!jwtUtil.validateToken(token)) {
            if (requestUri.startsWith("/api/predict/")) {
                log.info("[JWT] ⛔ INVALID TOKEN — URI={} | token-prefix={}...",
                        requestUri, token.substring(0, Math.min(20, token.length())));
            }
            filterChain.doFilter(request, response);
            return;
        }

        // ── 令牌有效——取出其中的声明（claims） ────────────────────────
        Long userId = jwtUtil.getUserId(token);
        String username = jwtUtil.getUsername(token);
        String role = jwtUtil.getRole(token);

        // ── 黑名单校验（属于基础设施能力，默认关闭） ──────────────────
        if (blacklistService.isEnabled()) {
            String jti = jwtUtil.getJti(token);
            if (blacklistService.isBlacklisted(jti)) {
                log.info("[JWT] ⛔ BLACKLISTED — URI={} | userId={} | username={} | jti={}",
                        requestUri, userId, username, jti);
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }
        }

        // ── 改密吊销校验（与 jti 黑名单相互独立） ──────────────────────
        // 重置密码会记录一个「按用户的时间点」，早于该时间点签发的令牌
        // 即使从未进入过黑名单，也必须立即失效。
        Date issuedAt = jwtUtil.getIssuedAt(token);
        if (issuedAt != null && blacklistService.isIssuedBeforeRevocation(userId, issuedAt.getTime())) {
            log.info("[JWT] ⛔ REVOKED BY PASSWORD CHANGE — URI={} | userId={}", requestUri, userId);
            SecurityContextHolder.clearContext();
            filterChain.doFilter(request, response);
            return;
        }

        // ── 写入认证信息 ──────────────────────────────────────────────
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())
        );

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null, authorities);
        authentication.setDetails(username);

        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.info("[JWT] ✅ AUTH OK — URI={} | userId={} | username={} | role=ROLE_{}",
                requestUri, userId, username, role.toUpperCase());

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
