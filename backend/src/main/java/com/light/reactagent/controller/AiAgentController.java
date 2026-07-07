package com.light.reactagent.controller;

import com.light.reactagent.agent.LightManus;
import com.light.reactagent.config.AgentRateLimiter;
import com.light.reactagent.service.KnowledgeRetrievalService;
import com.light.reactagent.service.UsageService;
import com.light.reactagent.tools.ToolRegistration;
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
 * <p>
 * 按前端能力开关动态装配工具子集：纯对话/网页搜索/知识库/双开。
 */
@RestController
@RequestMapping("/ai")
public class AiAgentController {

    @Resource
    private ChatModel dashscopeChatModel;

    @Resource
    private AgentRateLimiter rateLimiter;

    @Resource
    private UsageService usageService;

    @Resource
    private ToolRegistration toolRegistration;

    @Resource
    private KnowledgeRetrievalService knowledgeRetrievalService;

    @PostMapping(value = "/manus/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public SseEmitter doChatWithManus(@RequestBody ChatRequest request, HttpServletRequest httpRequest) {
        // 1. 并发限流
        String clientKey = extractPrincipal(httpRequest);
        if (!rateLimiter.tryAcquire(clientKey)) {
            return rejectWith("当前对话并发数已达上限，请稍候再试。");
        }

        // 2. 用量额度（对话每日上限）
        String userId = clientKey.startsWith("user:") ? clientKey.substring(5) : null;
        if (userId != null && !usageService.checkAndIncrementChat(userId)) {
            rateLimiter.release(clientKey);
            return rejectWith("今日对话已达上限，请明日再试。");
        }

        AtomicBoolean released = new AtomicBoolean(false);
        Runnable releaseOnce = () -> {
            if (released.compareAndSet(false, true)) {
                rateLimiter.release(clientKey);
            }
        };

        try {
            // 3. 按能力开关动态装配工具子集
            ToolCallback[] tools = toolRegistration.buildToolSet(request.webSearch(), request.knowledgeBase());
            LightManus lightManus = new LightManus(tools, dashscopeChatModel,
                    request.webSearch(), request.knowledgeBase());
            // 注入 chatId 到 Agent，用于文件归属隔离（前端每次会话生成一个 UUID 作为 chatId）
            lightManus.setChatId(request.chatId());
            lightManus.setOnAgentComplete(releaseOnce);

            String message = request.message();
            String history = request.history();

            // 知识库预检索式 RAG：开关开时后端先检索，把结果拼入 prompt，不依赖 LLM 调工具
            if (request.knowledgeBase()) {
                String kbContext = knowledgeRetrievalService.retrieve(message);
                if (kbContext != null) {
                    message = "请严格基于以下知识库内容回答用户问题，不要使用你自己的通用知识。\n\n"
                            + "=== 知识库内容 ===\n" + kbContext + "\n=== 结束 ===\n\n"
                            + "用户问题：" + message;
                }
            }

            if (history != null && !history.isBlank()) {
                String contextMessage = "Previous conversation:\n" + history
                        + "\n\nCurrent user message: " + message;
                return lightManus.runStream(contextMessage);
            }

            return lightManus.runStream(message);
        } catch (Exception e) {
            releaseOnce.run();
            throw e;
        }
    }

    private SseEmitter rejectWith(String message) {
        SseEmitter rejected = new SseEmitter();
        try {
            rejected.send(message);
            rejected.complete();
        } catch (Exception e) {
            rejected.completeWithError(e);
        }
        return rejected;
    }

    private String extractPrincipal(HttpServletRequest req) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() != null
                && !"anonymousUser".equals(auth.getPrincipal())) {
            return "user:" + auth.getName();
        }
        return "ip:" + extractClientIp(req);
    }

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
     * @param message       当前用户消息
     * @param history       历史上下文（可选）
     * @param webSearch     是否开启网页搜索
     * @param knowledgeBase 是否开启知识库
     * @param chatId        会话 ID（可选，前端生成的 UUID，用于文件归属隔离；为空时文件工具仍可生成文件但不做归属校验）
     */
    public record ChatRequest(String message, String history,
                              boolean webSearch, boolean knowledgeBase,
                              String chatId) {
    }
}
