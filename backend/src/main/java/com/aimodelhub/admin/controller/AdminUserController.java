package com.aimodelhub.admin.controller;

import com.aimodelhub.admin.audit.AuditLog;
import com.aimodelhub.common.api.PageVO;
import com.aimodelhub.common.api.Result;
import com.aimodelhub.common.exception.BusinessException;
import com.aimodelhub.security.TokenBlacklistService;
import com.aimodelhub.user.entity.SysUser;
import com.aimodelhub.user.mapper.SysUserMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户管理后台接口。
 * <p>
 * {@code /api/admin/**} 下的所有接口都由 Spring Security 的
 * {@code .hasRole("ADMIN")} 保护（见 {@link com.aimodelhub.security.SecurityConfig}）。
 * <p>
 * 提供用户分页查询与管理员重置密码。
 * 每次增删改都会通过 {@link AuditLog} 注解记录审计日志。
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private static final Logger log = LoggerFactory.getLogger(AdminUserController.class);

    /** 去掉了 0/O、1/l/I 等易混淆字符的字符集，避免人工转录出错。 */
    private static final String PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int TEMPORARY_PASSWORD_LENGTH = 12;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenBlacklistService tokenBlacklistService;

    public AdminUserController(SysUserMapper sysUserMapper,
                               PasswordEncoder passwordEncoder,
                               TokenBlacklistService tokenBlacklistService) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @PostConstruct
    void logRegistration() {
        log.info("AdminUserController registered at /api/admin/users");
    }

    /**
     * 分页查询全部用户。
     * <p>
     * 按 ID 升序返回，每页条数限制在 [1, 100]。
     * 实体上标了 {@code @JsonIgnore}，密码与密保答案不会出现在响应里。
     */
    @GetMapping
    public Result<PageVO<SysUser>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) {

        int clampedSize = Math.min(Math.max(1, size), 100);
        int pageIndex = Math.max(0, page);
        long total = sysUserMapper.count();
        int offset = pageIndex * clampedSize;
        List<SysUser> content = sysUserMapper.findAllOrderByIdAsc(offset, clampedSize);
        int totalPages = (int) Math.ceil((double) total / clampedSize);

        log.info("Admin listUsers: page={}/{}, total={} users",
                page, clampedSize, total);

        PageVO<SysUser> pageVO = new PageVO<>(
                content, total, totalPages, pageIndex, clampedSize);

        return Result.ok(pageVO);
    }

    /**
     * 把用户密码重置为一个随机临时密码。
     * <p>
     * 这是管理员发起的重置，不需要校验密保问题。
     * 临时密码只返回一次，由管理员通过安全渠道转交用户，且不写入审计日志。
     * 重置同时会吊销该用户已签发的全部令牌。
     */
    @AuditLog(action = "RESET_USER_PASSWORD", detail = "Admin resets a user's password")
    @PostMapping("/{id}/reset-password")
    public Result<Map<String, Object>> resetPassword(@PathVariable Long id) {
        SysUser user = sysUserMapper.findById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        String temporaryPassword = generateTemporaryPassword();
        user.setPassword(passwordEncoder.encode(temporaryPassword));
        user.onUpdate();
        sysUserMapper.update(user);

        // 让用旧密码建立的会话立即失效
        tokenBlacklistService.revokeAllForUser(user.getId(), System.currentTimeMillis());

        log.info("Admin reset password for user: id={}, username={}", user.getId(), user.getUsername());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("username", user.getUsername());
        data.put("temporaryPassword", temporaryPassword);
        data.put("notice", "临时密码仅显示一次，请通过安全渠道转交用户，并要求登录后立即修改");
        return Result.ok("密码重置成功", data);
    }

    private static String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder(TEMPORARY_PASSWORD_LENGTH);
        for (int i = 0; i < TEMPORARY_PASSWORD_LENGTH; i++) {
            sb.append(PASSWORD_ALPHABET.charAt(SECURE_RANDOM.nextInt(PASSWORD_ALPHABET.length())));
        }
        return sb.toString();
    }
}
