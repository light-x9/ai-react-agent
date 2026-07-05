package com.light.reactagent.controller;

import com.light.reactagent.agent.LightManus;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

    /**
     * 流式调用 SuperAgent，支持基于 chatId 的多轮对话记忆
     * <p>
     * 改用 POST + JSON Body（原为 GET + URL 参数），原因：
     * 1. 对话内容不再进入 URL —— 避免 Nginx access log / 浏览器历史 / APM 采集泄露
     * 2. 突破 URL 长度限制 —— 长 history 会超 Nginx 默认 4K/8K 上限
     * 3. 为 JWT 认证铺路 —— EventSource 只支持 GET 且无法携带 Authorization header，
     *    改 POST 后前端可用 fetch + ReadableStream 携带 Token
     *
     * @param request 包含 message（当前用户消息）和 history（历史上下文，可选）
     */
    @PostMapping(value = "/manus/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public SseEmitter doChatWithManus(@RequestBody ChatRequest request) {
        LightManus lightManus = new LightManus(allToolsWithMcp, dashscopeChatModel);

        String message = request.message();
        String history = request.history();

        // 将前端传来的历史消息注入 Agent 上下文（格式：User: xxx\nAssistant: yyy\n...）
        if (history != null && !history.isBlank()) {
            String contextMessage = "Previous conversation:\n" + history
                    + "\n\nCurrent user message: " + message;
            return lightManus.runStream(contextMessage);
        }

        return lightManus.runStream(message);
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
