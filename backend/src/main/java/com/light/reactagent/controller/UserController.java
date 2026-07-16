package com.light.reactagent.controller;

import com.light.reactagent.entity.User;
import com.light.reactagent.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 个人中心：查询 / 编辑用户信息、修改密码
 * <p>
 * 所有接口从 JWT 获取当前用户名，不传 userId，防止越权。
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** URL 校验：仅允许 http(s) 协议，防止 XSS */
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://.+");

    /**
     * 从 SecurityContext 获取当前用户名（JWT 过滤器写入的是 username 字符串）
     */
    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/profile")
    public Map<String, Object> getProfile() {
        String username = currentUsername();
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return Map.of("success", false, "message", "用户不存在");
        }
        return Map.of(
                "success", true,
                "username", user.getUsername(),
                "nickname", user.getNickname() != null ? user.getNickname() : "",
                "avatar", user.getAvatar() != null ? user.getAvatar() : "",
                "bio", user.getBio() != null ? user.getBio() : "",
                "createdAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : ""
        );
    }

    /**
     * 更新昵称 / 头像 / 简介
     */
    @PutMapping("/profile")
    public Map<String, Object> updateProfile(@RequestBody UpdateProfileRequest req) {
        String username = currentUsername();
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return Map.of("success", false, "message", "用户不存在");
        }

        // 昵称校验
        if (req.nickname() != null) {
            String nickname = req.nickname().trim();
            if (nickname.isEmpty()) {
                return Map.of("success", false, "message", "昵称不能为空");
            }
            if (nickname.length() > 50) {
                return Map.of("success", false, "message", "昵称不能超过 50 个字符");
            }
            user.setNickname(nickname);
        }

        // 头像 URL 校验
        if (req.avatar() != null) {
            String avatar = req.avatar().trim();
            if (!avatar.isEmpty() && !URL_PATTERN.matcher(avatar).matches()) {
                return Map.of("success", false, "message", "头像链接必须是合法的 http(s) URL");
            }
            user.setAvatar(avatar.isEmpty() ? null : avatar);
        }

        // 简介校验
        if (req.bio() != null) {
            String bio = req.bio().trim();
            if (bio.length() > 200) {
                return Map.of("success", false, "message", "简介不能超过 200 个字符");
            }
            user.setBio(bio.isEmpty() ? null : bio);
        }

        userRepository.save(user);
        return Map.of("success", true, "message", "保存成功");
    }

    /**
     * 修改密码（需校验原密码）
     */
    @PutMapping("/password")
    public Map<String, Object> changePassword(@RequestBody ChangePasswordRequest req) {
        // 新密码强度校验
        if (req.newPassword() == null || req.newPassword().length() < 6) {
            return Map.of("success", false, "message", "新密码至少 6 位");
        }
        if (req.newPassword().length() > 64) {
            return Map.of("success", false, "message", "新密码不能超过 64 位");
        }

        String username = currentUsername();
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return Map.of("success", false, "message", "用户不存在");
        }

        // 校验原密码
        if (req.oldPassword() == null || !passwordEncoder.matches(req.oldPassword(), user.getPassword())) {
            return Map.of("success", false, "message", "原密码错误");
        }

        user.setPassword(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);
        return Map.of("success", true, "message", "密码修改成功");
    }

    // ---- Request DTO ----

    public record UpdateProfileRequest(String nickname, String avatar, String bio) {
    }

    public record ChangePasswordRequest(String oldPassword, String newPassword) {
    }
}
