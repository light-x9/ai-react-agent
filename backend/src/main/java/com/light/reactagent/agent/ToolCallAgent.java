package com.light.reactagent.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.light.reactagent.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    private final ToolCallback[] availableTools;
    private ChatResponse toolCallChatResponse;
    private final ToolCallingManager toolCallingManager;
    private final ChatOptions chatOptions;

    /**
     * 单条工具返回内容截断上限（字符）。工具（如网页抓取）可能返回数万字原文，
     * 若不截断会被 Agent 每一步重发给 LLM，在长任务里随步数快速累积导致 token 超限 / 延迟飙升。
     */
    private static final int MAX_TOOL_RESPONSE_CHARS = 4000;

    // 当前步骤的思考内容与工具调用（供 step() 构建结构化 SSE 输出）
    private String currentThought;
    private List<Map<String, String>> currentToolCalls;

    public ToolCallAgent(ToolCallback[] availableTools) {
        super();
        this.availableTools = availableTools;
        this.toolCallingManager = ToolCallingManager.builder()
                .toolCallbackResolver(new StaticToolCallbackResolver(List.of(availableTools)))
                .build();
        this.chatOptions = DashScopeChatOptions.builder()
                .withInternalToolExecutionEnabled(false)
                .build();
    }

    @Override
    public boolean think() {
        if (StrUtil.isNotBlank(getNextStepPrompt())) {
            UserMessage userMessage = new UserMessage(getNextStepPrompt());
            getMessageList().add(userMessage);
        }
        List<Message> messageList = getMessageList();
        Prompt prompt = new Prompt(messageList, this.chatOptions);
        try {
            ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .toolCallbacks(availableTools)
                    .call()
                    .chatResponse();
            this.toolCallChatResponse = chatResponse;
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();
            String result = assistantMessage.getText();
            log.info(getName() + "的思考：" + result);
            log.info(getName() + "选择了" + toolCallList.size() + " 个工具来使用");

            // 保存当前思考内容和工具调用，供 step() 构建结构化 SSE 输出
            this.currentThought = result;
            this.currentToolCalls = toolCallList.stream()
                    .map(tc -> Map.of(
                            "name", tc.name(),
                            "arguments", tc.arguments()
                    ))
                    .collect(Collectors.toList());
            String toolCallInfo = toolCallList.stream()
                    .map(toolCall -> String.format("工具名称：%s，参数：%s", toolCall.name(), toolCall.arguments()))
                    .collect(Collectors.joining("\n"));
            log.info(toolCallInfo);
            if (toolCallList.isEmpty()) {
                getMessageList().add(assistantMessage);
                return false;
            } else {
                return true;
            }
        } catch (Exception e) {
            log.error(getName() + "的思考过程遇到了问题：" + e.getMessage());
            getMessageList().add(new AssistantMessage("处理时遇到了错误：" + e.getMessage()));
            return false;
        }
    }

    @Override
    public String act() {
        if (!toolCallChatResponse.hasToolCalls()) {
            return "没有工具需要调用";
        }
        // --- 获取 LLM 在本次响应中产出的文字内容 ---
        // 有些 LLM（如 qwen）可能在返回工具调用的同时产出文字回答
        // 例如：LLM 回复 "杭州今天天气晴朗..." 同时调用 doTerminate
        // 此时需要把文字回答也发送给前端，否则用户只能看到 "任务结束"
        String assistantText = "";
        if (toolCallChatResponse.getResult() != null) {
            AssistantMessage am = toolCallChatResponse.getResult().getOutput();
            if (am != null && StrUtil.isNotBlank(am.getText())) {
                assistantText = am.getText();
            }
        }
        Prompt prompt = new Prompt(getMessageList(), this.chatOptions);
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
        setMessageList(toolExecutionResult.conversationHistory());
        // 截断工具返回的超长内容，避免单轮 Agent 任务内 token 随步数累积膨胀
        truncateToolResponses();
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());
        boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                .anyMatch(response -> response.name().equals("doTerminate"));
        if (terminateToolCalled) {
            setState(AgentState.FINISHED);
        }
        String results = toolResponseMessage.getResponses().stream()
                .map(response -> {
                    String rawData = response.responseData();
                    return formatToolResultForDisplay(response.name(), rawData);
                })
                .collect(Collectors.joining("\n"));
        // 如果 LLM 在调用工具的同时产出了文字回答，放到最前面一起发送给用户
        if (StrUtil.isNotBlank(assistantText)) {
            results = assistantText + "\n" + results;
        }
        log.info(results);
        return results;
    }

    /**
     * 截断 messageList 中 ToolResponseMessage 的超长返回内容。
     * 仅影响喂给模型的内容，不影响返回给前端的展示文本（展示走 formatToolResultForDisplay，已单独处理）。
     */
    private void truncateToolResponses() {
        List<Message> messages = getMessageList();
        for (int i = 0; i < messages.size(); i++) {
            Message m = messages.get(i);
            if (m instanceof ToolResponseMessage trm) {
                List<ToolResponseMessage.ToolResponse> truncated = trm.getResponses().stream()
                        .map(r -> {
                            String data = r.responseData();
                            if (data != null && data.length() > MAX_TOOL_RESPONSE_CHARS) {
                                return new ToolResponseMessage.ToolResponse(
                                        r.id(), r.name(),
                                        data.substring(0, MAX_TOOL_RESPONSE_CHARS) + "\n...[工具返回内容过长已截断]");
                            }
                            return r;
                        })
                        .collect(Collectors.toList());
                messages.set(i, new ToolResponseMessage(truncated));
            }
        }
    }

    private String formatToolResultForDisplay(String toolName, String rawData) {
        if (rawData == null || rawData.isBlank()) {
            return "工具 " + toolName + "：执行完成";
        }
        return switch (toolName) {
            case "searchWeb" -> "🔍 搜索结果：\n" + rawData;
            case "scrapeWebPage" -> {
                int len = rawData.length();
                yield len > 300 ? "🌐 网页抓取完成（" + len + " 字符）" : "🌐 网页内容：\n" + rawData;
            }
            case "writeFile", "readFile" -> "📄 " + rawData;
            case "generatePDF" -> "📕 " + rawData;
            case "downloadResource" -> "⬇️ " + rawData;
            case "executeTerminalCommand" -> "💻 " + rawData;
            case "doTerminate" -> "任务结束";
            case "searchImage" -> "🖼️ 图片搜索完成";
            default -> "工具 " + toolName + " 执行完成";
        };
    }
}