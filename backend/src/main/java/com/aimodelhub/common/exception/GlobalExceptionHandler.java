package com.aimodelhub.common.exception;

import com.aimodelhub.common.api.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

/**
 * 把各类异常映射为 {@link Result} 响应体，并给出匹配的 HTTP 状态码。
 * 状态码约定
 *   异常HTTP含义
 *   {@link MethodArgumentNotValidException}400Bean Validation 校验失败
 *   {@link IllegalArgumentException}400参数不合法
 *   {@link BusinessException}400违反业务规则
 *   {@link UnauthorizedException} / {@link BadCredentialsException}401未登录或凭据无效
 *   {@link AccessDeniedException}403权限不足
 *   {@link NotFoundException}404资源不存在
 *   {@link RateLimitException}429请求过于频繁
 *   {@link RuntimeException}500服务端未预期错误（不返回内部信息）
 */

@RestControllerAdvice//将后端各层抛出的异常转化为统一格式的 Result<String> 响应体
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    /** 处理 Bean Validation 校验失败，返回 400 与第一条字段错误提示。 */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().isEmpty()
                ? "参数校验失败"
                : e.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return Result.badRequest(message);
    }

    /**
     * 缺少必填的查询参数（如 {@code POST /api/predict/image} 未传 {@code modelId}）。
     * <p>
     * 这类异常属于客户端问题，必须返回 4xx。此前没有任何处理器接住它，
     * 会被兜底的 {@code Exception} 分支当成 500「系统内部错误」，
     * 既误导调用方，也会污染服务端错误率监控。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.badRequest("缺少必要参数: " + e.getParameterName());
    }

    /**
     * 缺少 multipart 请求中的某个部分（如图片接口未上传 {@code file}）。
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> handleMissingPart(MissingServletRequestPartException e) {
        return Result.badRequest("缺少必要的请求部分: " + e.getRequestPartName());
    }

    /**
     * 参数类型不匹配（如 {@code modelId=abc}），返回 400 而不是 500。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return Result.badRequest("参数 " + e.getName() + " 的格式不正确");
    }

    /**
     * 请求体不是合法 JSON，或字段类型无法反序列化。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> handleUnreadableBody(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.badRequest("请求体格式错误，请检查 JSON 结构与字段类型");
    }

    /**
     * 请求方法不支持（如对只支持 POST 的接口发起 GET），返回 405。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<String> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return Result.error(405, "请求方法不支持: " + e.getMethod());
    }

    /**
     * Content-Type 与接口不匹配（如往 multipart 接口发 JSON），返回 415。
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public Result<String> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return Result.error(415, "不支持的 Content-Type: " + e.getContentType()
                + "，该接口要求: " + e.getSupportedMediaTypes());
    }

    /**
     * multipart 请求本身损坏（边界缺失、early EOF 等），返回 400。
     */
    @ExceptionHandler(MultipartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> handleMultipart(MultipartException e) {
        log.warn("multipart 请求解析失败: {}", e.getMessage());
        return Result.badRequest("文件上传请求格式错误");
    }

    @ExceptionHandler(BadCredentialsException.class)
    /** 处理登录凭据错误，返回 401（不区分用户名不存在还是密码错误）。 */
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<String> handleBadCredentials(BadCredentialsException e) {
        return Result.unauthorized("用户名或密码错误");
    }

    @ExceptionHandler(UnauthorizedException.class)
    /** 处理未认证异常，返回 401。 */
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<String> handleUnauthorized(UnauthorizedException e) {
        return Result.unauthorized(e.getMessage());
    }

    /** 处理 Spring 自带的 ResponseStatusException，按其状态码原样返回。 */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result<String>> handleResponseStatus(ResponseStatusException e) {
        log.debug("HTTP status exception: {} — {}", e.getStatusCode(), e.getReason());
        return ResponseEntity.status(e.getStatusCode())
                .body(Result.error(e.getStatusCode().value(), e.getReason()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    /** 处理权限不足，返回 403。 */
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<String> handleAccessDenied(AccessDeniedException e) {
        return Result.forbidden("权限不足");
    }

    @ExceptionHandler(NotFoundException.class)
    /** 处理资源不存在，返回 404。 */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<String> handleNotFound(NotFoundException e) {
        return Result.notFound(e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    /** 处理上传文件超限，返回 413 并提示大小上限。 */
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public Result<String> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        log.warn("上传文件超过限制: {}", e.getMessage());
        return Result.error(413, "上传文件过大，单次请求不能超过 5MB");
    }

    @ExceptionHandler(RateLimitException.class)
    /** 处理触发限流的请求，返回 429。 */
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public Result<String> handleRateLimit(RateLimitException e) {
        log.warn("请求限流: {}", e.getMessage());
        return Result.error(429, e.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    /** 处理业务规则异常，返回 400 并回显业务提示。 */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> handleBusiness(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.badRequest(e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    /** 处理非法参数异常，返回 400。 */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数异常: {}", e.getMessage());
        return Result.badRequest(e.getMessage() == null ? "参数不合法" : e.getMessage());
    }

    /**
     * 上游模型服务拒绝了本次调用（FastAPI 返回 4xx/5xx）。
     * 统一映射为 502，而不是当成本地 400/500 处理。
     */
    @ExceptionHandler(RestClientResponseException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Result<String> handleUpstream(RestClientResponseException e) {
        log.warn("模型服务返回错误 status={} body={}",
                e.getStatusCode().value(), e.getResponseBodyAsString());
        // 503 是模型服务主动声明的「该模型没加载起来」（例如权重缺失），
        // 与「服务整体挂了」不是一回事，给用户的提示要能区分开。
        if (e.getStatusCode().value() == 503) {
            return Result.error(502, "该模型当前不可用（未加载或权重缺失），请联系管理员");
        }
        return Result.error(502, "模型服务调用失败（HTTP " + e.getStatusCode().value() + "）");
    }

    /**
     * 完全连不上模型服务（连接被拒、超时等）。
     */
    @ExceptionHandler(RestClientException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Result<String> handleGateway(RestClientException e) {
        log.error("调用模型服务失败", e);
        return Result.error(502, "模型服务不可用，请确认 model-service 已启动");
    }

    /**
     * 其他未预期异常都视为服务端缺陷：打印堆栈，
     * 且不把内部异常信息返回给客户端。
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<String> handleRuntime(RuntimeException e) {
        log.error("未预期的运行时异常", e);
        return Result.fail("系统内部错误，请稍后重试");
    }

    @ExceptionHandler(Exception.class)
    /** 兜底处理所有未匹配的异常，返回 500，且不把内部信息暴露给调用方。 */
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<String> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail("系统内部错误，请稍后重试");
    }
}
