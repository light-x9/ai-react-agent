package com.light.reactagent.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局异常处理器
 * <p>
 * 统一兜底所有未捕获异常，避免原始异常信息（堆栈、SQL、内部路径）泄露到前端。
 * SSE 流中的错误已在 BaseAgent 中做安全处理，此处处理标准 HTTP 接口。
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ---------- 业务异常 ----------

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException e) {
        log.warn("[Security] 认证失败：{}", e.getMessage());
        return buildError(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException e) {
        log.warn("[Security] 拒绝访问：{}", e.getMessage());
        return buildError(HttpStatus.FORBIDDEN, "无权限访问该资源");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("[Param] 参数校验失败：{}", e.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("[Param] 缺少必要参数：{}", e.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, "缺少必要参数：" + e.getParameterName());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        log.warn("[Upload] 上传文件超出大小限制：{}", e.getMessage());
        return buildError(HttpStatus.PAYLOAD_TOO_LARGE, "文件大小超出限制（最大 10MB）");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException e) {
        // 404 不打印堆栈，用 debug 级别
        log.debug("[Resource] 资源未找到：{}", e.getMessage());
        return buildError(HttpStatus.NOT_FOUND, "请求的资源不存在");
    }

    // ---------- 兜底 ----------

    /**
     * 所有未专门处理的异常在此兜底，绝不暴露原始错误消息给前端
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("[Global] 未捕获的异常：{}", e.getMessage(), e);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误，请稍后再试");
    }

    // ---------- 工具方法 ----------

    private ResponseEntity<Map<String, Object>> buildError(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
