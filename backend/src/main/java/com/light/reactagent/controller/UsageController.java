package com.light.reactagent.controller;

import com.light.reactagent.service.UsageService;
import jakarta.annotation.Resource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 用量查询接口
 */
@RestController
@RequestMapping("/usage")
public class UsageController {

    @Resource
    private UsageService usageService;

    /**
     * 查询当前用户今日用量
     * 返回 {chatUsed, chatLimit, searchUsed, searchLimit}
     */
    @GetMapping("/today")
    public Map<String, Object> today() {
        return usageService.getTodayUsage(currentUserId());
    }

    private String currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() != null
                && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        throw new IllegalStateException("未认证");
    }
}
