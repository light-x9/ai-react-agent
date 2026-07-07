package com.light.reactagent.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具：签发、解析、校验
 * <p>
 * 使用 HS256，密钥从 jwt.secret 注入（生产环境用环境变量 JWT_SECRET 覆盖）。
 */
@Slf4j
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expiration;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expiration:86400000}") long expiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
        // 弱密钥检测：如果 secret 过短或看起来像默认值，打印警告
        if (secret.length() < 32 || secret.contains("dev") || secret.contains("change")) {
            log.warn("[JWT-WARN] 当前使用的是弱 JWT 密钥，严禁用于生产环境！");
            log.warn("[JWT-WARN] 请设置环境变量 JWT_SECRET（至少 32 字节随机字符串，生成方式：openssl rand -base64 48）");
        }
    }

    /**
     * 签发 token
     */
    public String generateToken(String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .signWith(key)
                .compact();
    }

    /**
     * 从 token 提取用户名
     */
    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * 校验 token 是否合法且未过期
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
