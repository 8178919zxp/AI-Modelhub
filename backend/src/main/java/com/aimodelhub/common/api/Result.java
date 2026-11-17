package com.aimodelhub.common.api;

/**
 * 统一的 RESTful 响应包装。
 * HTTP 状态码约定
 * 方法状态码用途
 *     {@code ok()}200成功
 *     {@code created()}201资源创建成功
 *     {@code badRequest()}400参数不合法
 *     {@code unauthorized()}401需要登录
 *     {@code forbidden()}403权限不足
 *     {@code notFound()}404资源不存在
 *     {@code fail()}500服务端内部错误
 */
public class Result<T> {
    public Integer code;
    public String message;
    public T data;

    public Result() {
    }

    public Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // ── 2xx 成功 ──────────────────────────────────────────────────

    /** 200 OK —— 操作成功 */
    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "success", data);
    }

    /** 200 OK —— 自定义提示语 */
    public static <T> Result<T> ok(String message, T data) {
        return new Result<>(200, message, data);
    }

    /** 201 Created —— 资源创建成功 */
    public static <T> Result<T> created(T data) {
        return new Result<>(201, "resource created", data);
    }

    /** 201 Created —— 自定义提示语 */
    public static <T> Result<T> created(String message, T data) {
        return new Result<>(201, message, data);
    }

    // ── 4xx 客户端错误 ────────────────────────────────────────────

    /** 400 Bad Request —— 参数不合法 / 校验未通过 */
    public static <T> Result<T> badRequest(String message) {
        return new Result<>(400, message, null);
    }

    /** 401 Unauthorized —— 需要登录或令牌无效 */
    public static <T> Result<T> unauthorized(String message) {
        return new Result<>(401, message, null);
    }

    /** 403 Forbidden —— 已登录但权限不足 */
    public static <T> Result<T> forbidden(String message) {
        return new Result<>(403, message, null);
    }

    /** 404 Not Found —— 资源不存在 */
    public static <T> Result<T> notFound(String message) {
        return new Result<>(404, message, null);
    }

    // ── 5xx 服务端错误 ────────────────────────────────────────────

    /** 500 Internal Server Error —— 未预期的通用错误 */
    public static <T> Result<T> fail(String message) {
        return new Result<>(500, message, null);
    }

    /** 自定义错误，并指定具体的 HTTP 状态码 */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }
}
