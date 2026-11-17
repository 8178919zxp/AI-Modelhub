package com.aimodelhub.common.exception;

/**
 * 未认证异常，映射为 HTTP 401。
 * <p>
 * 用于调用方未登录（令牌缺失或过期）的场景，
 * 与表示业务规则冲突的 {@link BusinessException} 区分。
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
