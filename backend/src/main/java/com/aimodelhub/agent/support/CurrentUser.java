package com.aimodelhub.agent.support;

import com.aimodelhub.common.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 从 Spring Security 上下文取当前用户 id。
 * <p>
 * <b>为什么单独抽出来？</b>
 * 这是整个 Agent 模块唯一允许「认定身份」的入口。
 * 如果每个 Service 各自 {@code SecurityContextHolder.getContext()}，
 * 迟早会有人写出「从请求体里读 userId」的代码——那就是水平越权的开始。
 * <p>
 * 项目里 {@code JwtAuthFilter} 把 {@code principal} 固定成 {@code Long userId}
 * （见 SecurityConfig 的说明），所以这里只认 {@code Long} 类型。
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /**
     * 取当前登录用户 id；未登录直接抛 401。
     * Agent 接口不允许匿名，因此这里不做「返回 null 让调用方自己判断」的宽容处理。
     */
    public static Long requireId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long userId) {
            return userId;
        }
        throw new UnauthorizedException("未登录或登录已过期，请重新登录");
    }

    /**
     * 要求管理员身份。
     * <p>
     * 为什么不只依赖 SecurityConfig 的 URL 规则：知识库写入是会**影响所有用户**
     * 的操作（投毒一条文档，所有对话都可能被带偏），因此在方法入口再校验一次角色，
     * 避免将来路由调整时把权限一起改掉。
     */
    public static void requireAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean admin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!admin) {
            throw new org.springframework.security.access.AccessDeniedException("仅管理员可执行此操作");
        }
    }
}
