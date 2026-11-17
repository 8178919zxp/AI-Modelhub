package com.aimodelhub.common.exception;

/**
 * 业务异常：业务规则被违反时抛出。
 * <p>
 * 由 {@link GlobalExceptionHandler} 统一处理为 HTTP 400。
 * 与 {@link RuntimeException} 的区别在于：它明确表示「业务规则不允许」，
 * 而不是意料之外的运行时错误。
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
