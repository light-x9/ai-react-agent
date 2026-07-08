package com.light.reactagent.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * 认证入口点：当用户未认证（无 token 或 token 无效/过期）访问受保护资源时触发。
 * <p>
 * 返回 401 JSON 而非默认的重定向（默认行为会导致"response already committed"错误）。
 * 同时显式写入 CORS 头，确保跨域场景下前端能正常读取响应。
 */
@Slf4j
@Component
public class AuthenticationEntryPointImpl implements AuthenticationEntryPoint {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("[Security] 未认证请求被拒绝: {} {} — {}", request.getMethod(), request.getRequestURI(), authException.getMessage());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        // 显式写入 CORS 头，确保跨域时前端能读取响应
        response.setHeader("Access-Control-Allow-Origin", request.getHeader("Origin") != null ? request.getHeader("Origin") : "*");
        response.setHeader("Access-Control-Allow-Credentials", "true");

        Map<String, Object> body = Map.of(
                "status", 401,
                "error", "Unauthorized",
                "message", "登录已过期或未登录，请重新登录",
                "path", request.getRequestURI()
        );
        response.getWriter().write(MAPPER.writeValueAsString(body));
    }
}
