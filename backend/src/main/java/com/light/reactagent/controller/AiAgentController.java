package com.light.reactagent.controller;

import com.light.reactagent.agent.LightManus;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

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
     *
     * @param message 用户消息
     * @param chatId  会话 ID（同一窗口保持不变，用于恢复历史上下文）
     */
    @GetMapping(value = "/manus/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public SseEmitter doChatWithManus(
            @RequestParam String message,
            @RequestParam(required = false, defaultValue = "") String history) {

        LightManus lightManus = new LightManus(allToolsWithMcp, dashscopeChatModel);

        // 将前端传来的历史消息注入Agent上下文（格式：User: xxx\nAssistant: yyy\n...）
        if (history != null && !history.isBlank()) {
            String contextMessage = "Previous conversation:\n" + history
                    + "\n\nCurrent user message: " + message;
            return lightManus.runStream(contextMessage);
        }

        return lightManus.runStream(message);
    }}