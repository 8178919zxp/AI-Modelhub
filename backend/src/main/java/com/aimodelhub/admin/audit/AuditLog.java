package com.aimodelhub.admin.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要记录审计日志的后端管理接口。
 * <p>
 * {@link AuditAspect} 会拦截标注了 {@code @AuditLog} 的方法，
 * 并写入一条结构化日志，包含：
 * <ul>
 *   <li>操作人（取自 JWT 的 SecurityContext）</li>
 *   <li>操作时间</li>
 *   <li>动作名称与说明</li>
 *   <li>方法入参（文件等大体积参数除外）</li>
 *   <li>执行结果（成功 / 失败）</li>
 * </ul>
 */
@Target(ElementType.METHOD)// 标记这个注解只能用在方法上
@Retention(RetentionPolicy.RUNTIME)// 标记这个注解在运行时保留
public @interface AuditLog {
    /** 动作简称，例如 "CREATE_MODEL"、"DELETE_MODEL"。 */
    String action();
    /** 便于人工阅读的说明，写入审计日志。 */
    String detail() default "";
}
