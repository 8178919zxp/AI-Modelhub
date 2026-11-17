package com.aimodelhub.admin.audit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Instant;
import java.util.StringJoiner;

/**
 * 审计日志切面：拦截标注了 {@link AuditLog @AuditLog} 的后端管理接口，
 * 输出结构化的审计日志。
 * <p>
 * 单条日志格式（每次操作一行）：
 * <pre>
 *   AUDIT | 2026-06-04T12:00:00Z | admin(uid=1) | CREATE_MODEL | modelName=ResNet,type=image | SUCCESS
 * </pre>
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger AUDIT_LOGGER = LoggerFactory.getLogger("AUDIT");

    /**
     * 环绕通知：拦截标注 {@link AuditLog} 的方法，记录操作人、动作、参数与结果。
     * 无论方法成功还是抛异常都会落一条日志。
     */
    @Around("@annotation(audit)")
    public Object audit(ProceedingJoinPoint joinPoint, AuditLog audit) throws Throwable {
        // 解析操作人、动作、参数
        String operator = resolveOperator();
        // 获取当前时间、动作、参数
        String timestamp = Instant.now().toString();
        String action = audit.action();
        // 拼接参数串
        String args = summarizeArgs(joinPoint);

        // 执行方法，记录开始时间
        long start = System.currentTimeMillis();
        Object result;
        // 执行方法，记录开始时间
        String outcome = "UNKNOWN";
        try {
            // 执行方法，记录开始时间
            result = joinPoint.proceed();
            outcome = "SUCCESS";
        } catch (Throwable t) {
            outcome = "FAILURE(" + t.getClass().getSimpleName() + ": " + t.getMessage() + ")";
            throw t;
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            AUDIT_LOGGER.info("AUDIT | {} | {} | {} | {} | args=[{}] | {} | {}ms",
                    timestamp, operator, action, audit.detail(), args, outcome, elapsed);
        }

        return result;
    }

    // ── 辅助方法 ─────────────────────────────────────────────────────

    private String resolveOperator() {
        try {
            // 从安全上下文中获取当前用户
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            // 如果已认证，则获取用户信息
            if (auth != null && auth.isAuthenticated()) {
                // 从认证对象中获取用户信息
                Object principal = auth.getPrincipal();
                if (principal instanceof Long uid) {
                    return "uid=" + uid + "(" + auth.getName() + ")";
                }
                return auth.getName();
            }
        } catch (Exception ignored) {}
        return "anonymous";
    }

    /** 拼接精简后的参数串，遇到文件等大体积对象直接跳过。 */
    private String summarizeArgs(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Parameter[] params = method.getParameters();
        Object[] values = joinPoint.getArgs();

        StringJoiner sj = new StringJoiner(", ");
        for (int i = 0; i < params.length; i++) {
            String name = params[i].getName();
            Object value = values[i];
            if (value == null) {
                sj.add(name + "=null");
            } else if (value instanceof org.springframework.web.multipart.MultipartFile) {
                sj.add(name + "=<file>");  // 跳过二进制大对象
            } else if (value instanceof byte[]) {
                sj.add(name + "=<byte[" + ((byte[]) value).length + "]>");
            } else {
                String str = value.toString();
                if (str.length() > 120) {
                    str = str.substring(0, 120) + "...";
                }
                sj.add(name + "=" + str);
            }
        }
        return sj.toString();
    }
}
