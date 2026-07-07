package com.light.reactagent.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证接口频率限制过滤器
 * <p>
 * 对 /auth/login 和 /auth/register 按 IP 做简单频率限制，防止暴力破解。
 * 默认每秒 5 次，使用内存滑动窗口。
 * <p>
 * 注意：这是一个简易实现，适合单机部署。多实例部署时需要替换为 Redis 方案。
 */
@Slf4j
@Component
public class AuthRateLimitFilter implements Filter {

    /** 每秒最大请求次数 */
    private static final int MAX_REQUESTS_PER_SECOND = 5;
    /** 窗口大小（毫秒） */
    private static final long WINDOW_MS = 1000;

    /** key: IP, value: [windowStart, count] */
    private final Map<String, long[]> counters = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String path = req.getRequestURI();
        // 只拦截认证接口
        boolean isAuthPath = path.endsWith("/auth/login") || path.endsWith("/auth/register");
        if (!isAuthPath) {
            chain.doFilter(request, response);
            return;
        }

        String ip = getClientIp(req);
        long now = System.currentTimeMillis();
        long[] entry = counters.compute(ip, (k, v) -> {
            if (v == null || now - v[0] > WINDOW_MS) {
                return new long[]{now, 1};
            }
            v[1]++;
            return v;
        });

        if (entry[1] > MAX_REQUESTS_PER_SECOND) {
            log.warn("[RateLimit] IP {} 登录/注册频率过高（{}次/秒），已拒绝", ip, entry[1]);
            res.setStatus(429); // Too Many Requests
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"error\":\"请求过于频繁，请稍后再试\",\"status\":429}");
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * 获取客户端真实 IP（考虑反向代理场景）
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For 可能包含多个 IP，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
