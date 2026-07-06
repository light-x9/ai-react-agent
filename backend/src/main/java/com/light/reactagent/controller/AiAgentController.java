package com.light.reactagent.controller;

import com.light.reactagent.agent.LightManus;
import com.light.reactagent.config.AgentRateLimiter;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI Agent 流式接口
 */
@RestController
@RequestMapping("/ai")
public class AiAgentController {

    // 合并后的工具集（本地工具 + MCP 外部工具），供 SuperAgent 使用
    @Resource
    private ToolCallback[] allToolsWithMcp;

    @Resource
    private ChatModel dashscopeChatModel;

    @Resource
    private AgentRateLimiter rateLimiter;

    /**
     * 流式调用 SuperAgent，支持基于 chatId 的多轮对话记忆
     * <p>
     * 需 JWT 认证（SecurityConfig 保护 /ai/**）。限流 key 优先用已认证用户名，无则回退 IP。
     *
     * @param request     包含 message（当前用户消息）和 history（历史上下文，可选）
     * @param httpRequest 用于提取客户端 IP（限流回退 key）
     */
    @PostMapping(value = "/manus/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public SseEmitter doChatWithManus(@RequestBody ChatRequest request, HttpServletRequest httpRequest) {
        // 1. 并发限流：优先按用户，回退 IP
        String clientKey = extractPrincipal(httpRequest);
        if (!rateLimiter.tryAcquire(clientKey)) {
            SseEmitter rejected = new SseEmitter();
            try {
                rejected.send("当前对话并发数已达上限，请稍候再试。");
                rejected.complete();
            } catch (Exception e) {
                rejected.completeWithError(e);
            }
            return rejected;
        }

        // 保证并发许可只释放一次（runStream 的 finally 与 onTimeout 回调都可能触发）
        AtomicBoolean released = new AtomicBoolean(false);
        Runnable releaseOnce = () -> {
            if (released.compareAndSet(false, true)) {
                rateLimiter.release(clientKey);
            }
        };

        try {
            LightManus lightManus = new LightManus(allToolsWithMcp, dashscopeChatModel);
            lightManus.setOnAgentComplete(releaseOnce);

            String message = request.message();
            String history = request.history();

            // 将前端传来的历史消息注入 Agent 上下文（格式：User: xxx\nAssistant: yyy\n...）
            if (history != null && !history.isBlank()) {
                String contextMessage = "Previous conversation:\n" + history
                        + "\n\nCurrent user message: " + message;
                return lightManus.runStream(contextMessage);
            }

            return lightManus.runStream(message);
        } catch (Exception e) {
            // 同步阶段异常，手动释放许可，避免泄漏
            releaseOnce.run();
            throw e;
        }
    }

    /**
     * 提取限流 key：优先用已认证用户名，无认证时回退客户端 IP
     */
    private String extractPrincipal(HttpServletRequest req) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() != null
                && !"anonymousUser".equals(auth.getPrincipal())) {
            return "user:" + auth.getName();
        }
        return "ip:" + extractClientIp(req);
    }

    /**
     * 提取客户端真实 IP（穿透常见反向代理头）
     */
    private String extractClientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            return ip.split(",")[0].trim();
        }
        ip = req.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank()) {
            return ip;
        }
        return req.getRemoteAddr();
    }

    /**
     * 对话请求体
     *
     * @param message 当前用户消息（必填）
     * @param history 历史上下文（可选，格式 "User: ...\nAssistant: ..."）
     */
    public record ChatRequest(String message, String history) {
    }
}
