package com.light.reactagent.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT 认证过滤器
 * <p>
 * 从 Authorization: Bearer xxx 提取 token，校验通过后设置 SecurityContext。
 * 对 SSE 接口同样生效（前端 fetch 流式带 header）。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // 1. 从请求头提取 Authorization
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            // 2. 去掉 "Bearer " 前缀拿到纯 token
            String token = header.substring(7);
            // 3. 校验 token 合法且未过期
            if (jwtUtil.validateToken(token)) {
                // 4. 从 token 中取出用户名，构造一个已认证凭证（无需密码、无需权限列表）
                String username = jwtUtil.extractUsername(token);
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                // 5. 写入 SecurityContext，后续 Spring Security 就知道"这个请求已经登录了"
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
            // token 校验失败时不设认证，继续往下走；SecurityConfig 的 anyRequest().authenticated() 会返回 403
        }
        // 6. 放行请求到下一个过滤器
        chain.doFilter(request, response);
    }
}
