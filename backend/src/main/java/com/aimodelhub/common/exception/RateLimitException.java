package com.aimodelhub.common.exception;

/**
 * 限流异常：用户请求频率超过允许值时抛出。
 * <p>
 * 由 {@link GlobalExceptionHandler} 处理为 HTTP 429（Too Many Requests）。
 * 刻意不继承 {@link BusinessException}，因为限流应返回 429 而不是 400。
 */
public class RateLimitException extends RuntimeException {

    public RateLimitException(String message) {
        super(message);
    }

    public RateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
