package com.aimodelhub.common.exception;

/**
 * 资源不存在异常，映射为 HTTP 404。
 * <p>
 * 与 {@link BusinessException}（HTTP 400）区分开，
 * 便于客户端分辨「请求有问题」还是「资源确实不存在」。
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
