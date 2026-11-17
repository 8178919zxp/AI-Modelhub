package com.aimodelhub.auth.controller;

import com.aimodelhub.auth.dto.ForgotPasswordQuestionDTO;
import com.aimodelhub.auth.dto.ForgotPasswordResetDTO;
import com.aimodelhub.auth.dto.LoginDTO;
import com.aimodelhub.auth.dto.RegisterDTO;
import com.aimodelhub.auth.vo.LoginVO;
import com.aimodelhub.common.api.Result;
import com.aimodelhub.common.exception.UnauthorizedException;
import com.aimodelhub.security.JwtUtil;
import com.aimodelhub.security.TokenBlacklistService;
import com.aimodelhub.user.entity.SysUser;
import com.aimodelhub.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;

    public AuthController(UserService userService,
                          JwtUtil jwtUtil,
                          TokenBlacklistService tokenBlacklistService) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    /**
     * 账号密码登录。
     * 校验通过后签发 JWT，并返回用户基本信息与令牌。
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        String token = userService.login(dto.username, dto.password);
        SysUser user = userService.getUserByUsername(dto.username);

        LoginVO vo = new LoginVO(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getRole(),
                user.getAvatar(),
                token
        );
        return Result.ok("登录成功", vo);
    }

    /**
     * 注册新用户。
     * 用户名重复时返回业务异常；注册成功后可直接用于登录。
     */
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@Valid @RequestBody RegisterDTO dto) {
        SysUser user = userService.register(dto.username, dto.password, dto.nickname,
                dto.securityQuestion, dto.securityAnswer);
        return Result.ok("注册成功", Map.of(
                "username", user.getUsername(),
                "nickname", user.getNickname(),
                "role", user.getRole()
        ));
    }

    /**
     * 找回密码第一步：按用户名取密保问题。
     * <p>
     * 用户存在且设置过密保问题时返回问题本身。
     * 用户不存在或未设置密保时统一抛 BusinessException（提示语一致，避免被枚举账号）。
     */
    @PostMapping("/forgot-password/question")
    public Result<Map<String, String>> getSecurityQuestion(
            @RequestBody ForgotPasswordQuestionDTO dto) {
        String question = userService.getSecurityQuestion(dto.getUsername());
        return Result.ok(Map.of("securityQuestion", question));
    }

    /**
     * 找回密码第二步：用密保答案重置密码。
     * <p>
     * 先校验答案与库中 BCrypt 哈希是否匹配，通过后写入新密码。
     */
    @PostMapping("/forgot-password/reset")
    public Result<String> resetPassword(
            @RequestBody ForgotPasswordResetDTO dto) {
        userService.resetPassword(dto.getUsername(), dto.getSecurityAnswer(),
                dto.getNewPassword());
        return Result.ok("密码重置成功，请使用新密码登录");
    }

    /**
     * 退出登录：把当前 JWT 的 {@code jti} 加入 Redis 黑名单，使其立即失效。
     * <p>
     * 该接口要求携带有效 Bearer 令牌（由 Spring Security 的
     * {@code .anyRequest().authenticated()} 保证）。令牌从 {@code Authorization}
     * 请求头取出，其 {@code jti} 按令牌剩余有效期写入黑名单。
     * <p>
     * <b>Redis 不可用时的行为：</b>响应中 {@code "blacklisted": false}，
     * 告知客户端服务端吊销未完成。客户端仍会清除本地令牌，
     * 最坏情况是该令牌在自然过期前继续可用。
     */
    @PostMapping("/logout")
    public Result<Map<String, Object>> logout(HttpServletRequest request) {
        // 1. 从 Authorization 请求头取出 Bearer 令牌
        String token = extractBearerToken(request);
        if (token == null) {
            throw new UnauthorizedException("未提供认证令牌，请确认已登录");
        }

        // 2. 解析令牌取出 jti（早期签发、载荷中没有 jti 的令牌会解析失败）
        String jti = jwtUtil.getJti(token);
        if (jti == null) {
            log.warn("Logout attempted with token lacking jti claim — token may be from old version");
            return Result.ok("已退出登录（令牌版本过旧，服务端未注销）", Map.of(
                    "blacklisted", false,
                    "reason", "token_missing_jti"
            ));
        }

        // 3. 计算剩余有效期
        Date expiration = jwtUtil.getExpiration(token);
        long remainingMs = (expiration != null)
                ? expiration.getTime() - System.currentTimeMillis()
                : 0;

        // 4. 把 jti 写入 Redis 黑名单（失败时降级处理）
        boolean blacklisted = false;
        String blacklistReason = null;

        if (remainingMs <= 0) {
            // 令牌已过期，无需加入黑名单
            blacklistReason = "token_already_expired";
            log.debug("Logout for already-expired token: jti={}", jti);
        } else {
            try {
                tokenBlacklistService.blacklist(jti, remainingMs);
                // 复核黑名单是否写入成功（双重保险）
                blacklisted = tokenBlacklistService.isBlacklisted(jti);
                if (blacklisted) {
                    log.info("Token blacklisted on logout: jti={}, remainingMs={}", jti, remainingMs);
                } else {
                    blacklistReason = "redis_write_unverified";
                    log.warn("Blacklist write unverified for jti={} — may be Redis connection issue", jti);
                }
            } catch (Exception e) {
                blacklistReason = "redis_unavailable";
                log.warn("Failed to blacklist token on logout (jti={}): {}", jti, e.getMessage());
            }
        }

        // 5. 组装响应
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("blacklisted", blacklisted);
        if (blacklistReason != null) {
            data.put("reason", blacklistReason);
        }

        String message = blacklisted
                ? "已退出登录"
                : "已退出登录（服务端令牌注销未完成，令牌将在到期后自动失效）";

        return Result.ok(message, data);
    }

    /** 从 Authorization 请求头取出 Bearer 令牌，取不到返回 null。 */
    private String extractBearerToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
