package com.light.reactagent.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * 访问拒绝处理器：当用户已认证但无权限访问资源时触发。
 * <p>
 * 返回 403 JSON。在本系统中一般不会触发（所有已认证用户权限相同），
 * 但作为防御性配置保留，避免异常泄露导致响应崩溃。
 */
@Slf4j
@Component
public class AccessDeniedHandlerImpl implements AccessDeniedHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("[Security] 访问被拒绝: {} {} — {}", request.getMethod(), request.getRequestURI(), accessDeniedException.getMessage());

        response.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", request.getHeader("Origin") != null ? request.getHeader("Origin") : "*");
        response.setHeader("Access-Control-Allow-Credentials", "true");

        Map<String, Object> body = Map.of(
                "status", 403,
                "error", "Forbidden",
                "message", "无权访问该资源",
                "path", request.getRequestURI()
        );
        response.getWriter().write(MAPPER.writeValueAsString(body));
    }
}
