package com.light.reactagent.controller;

import com.light.reactagent.entity.User;
import com.light.reactagent.repository.UserRepository;
import com.light.reactagent.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认证接口：注册 / 登录
 * <p>
 * 路径 /auth/** 在 SecurityConfig 中放行，无需 token。
 * 注册成功直接返回 token，省去登录步骤。
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * 注册：创建用户并返回 token
     */
    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody RegisterRequest req) {
        if (req.username() == null || req.username().isBlank()
                || req.password() == null || req.password().length() < 6) {
            return Map.of("success", false, "message", "用户名不能为空且密码至少 6 位");
        }
        if (userRepository.existsByUsername(req.username())) {
            return Map.of("success", false, "message", "用户名已存在");
        }
        User user = new User();
        user.setUsername(req.username());
        user.setPassword(passwordEncoder.encode(req.password()));
        userRepository.save(user);
        String token = jwtUtil.generateToken(user.getUsername());
        return Map.of("success", true, "token", token, "username", user.getUsername());
    }

    /**
     * 登录：校验密码后签发 token
     */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req) {
        User user = userRepository.findByUsername(req.username()).orElse(null);
        if (user == null || !passwordEncoder.matches(req.password(), user.getPassword())) {
            return Map.of("success", false, "message", "用户名或密码错误");
        }
        String token = jwtUtil.generateToken(user.getUsername());
        return Map.of("success", true, "token", token, "username", user.getUsername());
    }

    public record RegisterRequest(String username, String password) {
    }

    public record LoginRequest(String username, String password) {
    }
}
