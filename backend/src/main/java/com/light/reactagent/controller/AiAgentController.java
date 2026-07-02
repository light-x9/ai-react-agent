package com.light.reactagent.controller;

import com.light.reactagent.agent.YuManus;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/ai")
public class AiAgentController {

    // 合并后的工具集（本地工具 + MCP 外部工具），供 SuperAgent 使用
    @Resource
    private ToolCallback[] allToolsWithMcp;

    @Resource
    private ChatModel dashscopeChatModel;

    // 流式调用 SuperAgent（使用合并工具：本地工具 + MCP 外部工具）
    @GetMapping(value = "/manus/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public SseEmitter doChatWithManus(String message) {
        YuManus yuManus = new YuManus(allToolsWithMcp, dashscopeChatModel);
        return yuManus.runStream(message);
    }
}