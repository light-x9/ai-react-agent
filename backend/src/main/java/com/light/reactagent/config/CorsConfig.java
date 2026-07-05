package com.light.reactagent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 全局跨域配置
 * <p>
 * 前端 fetch POST + application/json 是非简单请求，会触发预检 OPTIONS。
 * 这里显式列出 Allow-Headers，避免 allowCredentials=true 时通配符 "*" 在某些浏览器下不被接受。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // 允许携带 Cookie（为后续基于 Cookie 的会话留口子）
                .allowCredentials(true)
                // 必须用 patterns，否则 "*" 会和 allowCredentials 冲突
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                // 显式声明允许的请求头，覆盖 Authorization / Content-Type / Accept
                .allowedHeaders("Authorization", "Content-Type", "Accept")
                .exposedHeaders("Content-Disposition");
    }
}
