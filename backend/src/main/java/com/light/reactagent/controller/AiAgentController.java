package com.light.reactagent.controller;

import com.light.reactagent.agent.LightManus;
import com.light.reactagent.config.AgentRateLimiter;
import com.light.reactagent.service.KnowledgeRetrievalService;
import com.light.reactagent.service.UsageService;
import com.light.reactagent.tools.ToolRegistration;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CompletableFuture;
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
    private ChatModel openaiChatModel;

    @Resource
    private AgentRateLimiter rateLimiter;

    @Resource
    private UsageService usageService;

    @Resource
    private ToolRegistration toolRegistration;

    @Resource
    private KnowledgeRetrievalService knowledgeRetrievalService;

    /** 快速直答模式下的身份指令（SystemMessage，权重高于普通对话） */
    private static final String QUICK_REPLY_IDENTITY = """
            IMPORTANT — YOUR IDENTITY:
            Your name is Light (小光). You are NOT DeepSeek. You were NOT created by the DeepSeek company.
            You are a helpful AI assistant. Keep answers warm, concise, and in the same language as the user.
            When asked "what is your name" or "who are you" or "你叫什么" or "你是谁", you MUST answer:
            "我叫 Light，也可以叫我小光，是一个智能助手。有什么可以帮你的？"
            NEVER identify yourself as DeepSeek under any circumstances. NEVER mention DeepSeek.
            """;

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
            String message = request.message();
            String history = request.history();

            // 知识库预检索式 RAG：无论是否深度思考，开关开时后端先检索，把结果拼入 prompt
            // 这样「深度思考」关闭（快速直答）时也能基于知识库回答
            if (request.knowledgeBase()) {
                String kbContext = knowledgeRetrievalService.retrieve(message);
                if (kbContext != null) {
                    message = "请严格基于以下知识库内容回答用户问题，不要使用你自己的通用知识。\n\n"
                            + "=== 知识库内容 ===\n" + kbContext + "\n=== 结束 ===\n\n"
                            + "用户问题：" + message;
                }
            }

            // 拼接历史上下文
            String finalPrompt = message;
            if (history != null && !history.isBlank()) {
                finalPrompt = "Previous conversation:\n" + history
                        + "\n\nCurrent user message: " + message;
            }

            // 「深度思考」关闭 → 快速直答：不进 Agent ReAct 循环，ChatModel 单轮调用
            // webSearch 字段语义现为「是否深度模式」，字段名保持不变以兼容前端协议
            if (!request.webSearch()) {
                return quickReply(finalPrompt, releaseOnce);
            }

            // 「深度思考」开启 → Agent 模式：多步 ReAct 推理 + 工具调用
            ToolCallback[] tools = toolRegistration.buildToolSet(true, request.knowledgeBase());
            LightManus lightManus = new LightManus(tools, openaiChatModel,
                    true, request.knowledgeBase());
            // 注入 chatId 到 Agent，用于文件归属隔离（前端每次会话生成一个 UUID 作为 chatId）
            lightManus.setChatId(request.chatId());
            lightManus.setOnAgentComplete(releaseOnce);
            return lightManus.runStream(finalPrompt);
        } catch (Exception e) {
            releaseOnce.run();
            throw e;
        }
    }

    /**
     * 快速直答模式：深度思考关闭时使用。
     * <p>
     * 不进入 Agent ReAct 循环，直接用 ChatModel 单轮调用，
     * 将回答通过 SSE final 事件返回（与 Agent 模式输出格式一致，前端无需区分）。
     *
     * @param promptText  已拼接历史上下文与知识库 RAG 结果的完整用户提示
     * @param onComplete  完成回调（释放并发许可等）
     */
    private SseEmitter quickReply(String promptText, Runnable onComplete) {
        SseEmitter emitter = new SseEmitter(120000L); // 2 分钟超时
        CompletableFuture.runAsync(() -> {
            try {
                // 使用 SystemMessage 下发身份指令，比拼在 UserMessage 里权重更高
                SystemMessage systemMsg = new SystemMessage(QUICK_REPLY_IDENTITY);
                UserMessage userMsg = new UserMessage(promptText);
                Prompt prompt = new Prompt(List.of(systemMsg, userMsg));
                ChatResponse response = openaiChatModel.call(prompt);
                String answer = response.getResult().getOutput().getText();
                if (answer == null) {
                    answer = "";
                }
                // 与 Agent 模式一致的事件格式：final 事件 + [DONE]
                emitter.send("{\"type\":\"final\",\"content\":\"" + escapeJson(answer) + "\"}");
                emitter.send("[DONE]");
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send("{\"type\":\"error\",\"content\":\"快速回答失败："
                            + escapeJson(String.valueOf(e.getMessage())) + "\"}");
                    emitter.send("[DONE]");
                    emitter.complete();
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
            } finally {
                onComplete.run();
            }
        });
        return emitter;
    }

    /**
     * 简单 JSON 字符串转义，防止 SSE 解析中断
     */
    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
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
     * @param webSearch     是否开启「深度思考」（Agent 模式）：true=多步推理+工具调用，false=快速直答
     * @param knowledgeBase 是否开启知识库（RAG 预检索，两种模式下都生效）
     * @param chatId        会话 ID（可选，前端生成的 UUID，用于文件归属隔离；为空时文件工具仍可生成文件但不做归属校验）
     */
    public record ChatRequest(String message, String history,
                              boolean webSearch, boolean knowledgeBase,
                              String chatId) {
    }
}
