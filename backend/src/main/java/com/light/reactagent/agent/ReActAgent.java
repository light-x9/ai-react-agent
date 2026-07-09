package com.light.reactagent.agent;

import com.light.reactagent.agent.model.AgentState;
import com.light.reactagent.config.SpringBeanUtils;
import com.light.reactagent.tools.file.FileContextHolder;
import com.light.reactagent.tools.file.FileMetadata;
import com.light.reactagent.tools.file.FileMetadataManager;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * ReAct (Reasoning and Acting) 模式的代理抽象类
 * 实现了思考-行动的循环模式，step() 返回结构化 JSON 供前端 ReActSteps 组件渲染
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public abstract class ReActAgent extends BaseAgent {

    /**
     * 处理当前状态并决定下一步行动
     *
     * @return 是否需要执行行动，true表示需要执行，false表示不需要执行
     */
    public abstract boolean think();

    /**
     * 执行决定的行动
     *
     * @return 行动执行结果
     */
    public abstract String act();

    /**
     * 执行单个步骤：思考和行动。返回多行 JSON，每行是一个 SSE 事件：
     *   {"type":"thought","content":"..."}
     *   {"type":"action","tool":"...","params":{...}}
     *   {"type":"observation","summary":"..."}
     *   {"type":"final","content":"..."}
     */
    @Override
    public String step() {
        try {
            // 先思考
            boolean shouldAct = think();
            StringBuilder sb = new StringBuilder();

            // 1. 提取思考内容
            String thought = extractThought();
            sb.append("{\"type\":\"thought\",\"content\":\"")
              .append(escapeJson(thought))
              .append("\"}\n");

            if (!shouldAct) {
                // AI 没有请求工具调用，说明本轮回答已完成
                setState(AgentState.FINISHED);
                // 获取 AI 的文字回复作为最终答案
                String finalText = extractAssistantText();
                setFinalAnswer(finalText);
                sb.append("{\"type\":\"final\",\"content\":\"")
                  .append(escapeJson(finalText))
                  .append("\"");
                // 附带本次请求生成的文件列表（供前端展示下载卡片）
                appendGeneratedFilesJson(sb);
                sb.append("}");
                return sb.toString();
            }

            // 2. 有工具调用 → 发送 action 事件
            List<Map<String, String>> toolCalls = extractToolCalls();
            for (Map<String, String> tc : toolCalls) {
                String toolName = tc.getOrDefault("name", "unknown");
                String toolArgs = tc.getOrDefault("arguments", "{}");
                sb.append("{\"type\":\"action\",\"tool\":\"")
                  .append(escapeJson(toolName))
                  .append("\",\"params\":")
                  .append(toolArgs)
                  .append("}\n");
            }

            // 3. 执行工具并发送 observation 事件
            String actionResult = act();
            sb.append("{\"type\":\"observation\",\"summary\":\"")
              .append(escapeJson(actionResult))
              .append("\"}");

            // 4. 如果 act() 触发了 doTerminate（state→FINISHED），补发 final 事件
            //    否则前端只消费 final 事件，会一直停留在「思考中…」
            //    （LLM 调用 doTerminate 结束时，其文字回答即为最终答案）
            if (getState() == AgentState.FINISHED) {
                String finalText = extractAssistantText();
                setFinalAnswer(finalText);
                sb.append("\n{\"type\":\"final\",\"content\":\"")
                  .append(escapeJson(finalText))
                  .append("\"");
                appendGeneratedFilesJson(sb);
                sb.append("}");
            }
            return sb.toString();

        } catch (Exception e) {
            log.error("step execution failed", e);
            return "{\"type\":\"error\",\"content\":\"" + escapeJson(e.getMessage()) + "\"}";
        }
    }

    /**
     * 从 ToolCallAgent（或子类）中提取思考内容
     */
    private String extractThought() {
        if (this instanceof ToolCallAgent tca) {
            String t = tca.getCurrentThought();
            return t != null ? t : "";
        }
        // 非 ToolCallAgent：从最后一条 AssistantMessage 提取
        return extractAssistantText();
    }

    /**
     * 从 ToolCallAgent（或子类）中提取工具调用列表
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, String>> extractToolCalls() {
        if (this instanceof ToolCallAgent tca) {
            var calls = tca.getCurrentToolCalls();
            return calls != null ? calls : List.of();
        }
        return List.of();
    }

    /**
     * 从消息列表中提取最后一条非空的 AssistantMessage 文本
     */
    private String extractAssistantText() {
        var messages = getMessageList();
        for (int i = messages.size() - 1; i >= 0; i--) {
            var msg = messages.get(i);
            if (msg instanceof org.springframework.ai.chat.messages.AssistantMessage am) {
                String text = am.getText();
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return "对话结束";
    }

    /**
     * 将本次请求生成的文件列表以 JSON 数组形式追加到 StringBuilder
     * <p>
     * 输出格式：,"files":[{"fileId":"xxx","name":"report.pdf","size":12345,"type":"pdf"},...]
     * 无文件时不追加任何内容。
     */
    private void appendGeneratedFilesJson(StringBuilder sb) {
        List<String> fileIds = FileContextHolder.getGeneratedFileIds();
        if (fileIds.isEmpty()) {
            return;
        }
        // 通过 FileMetadataManager 获取元数据（需要从 Spring 上下文获取）
        FileMetadataManager metadataManager = getMetadataManager();
        if (metadataManager == null) {
            return;
        }
        String chatId = FileContextHolder.getChatId();
        sb.append(",\"files\":[");
        boolean first = true;
        for (String fileId : fileIds) {
            FileMetadata meta = metadataManager.getFile(chatId, fileId);
            if (meta == null) {
                continue;
            }
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("{\"fileId\":\"").append(meta.getFileId()).append("\"")
              .append(",\"name\":\"").append(escapeJson(meta.getOriginalName())).append("\"")
              .append(",\"size\":").append(meta.getSizeBytes())
              .append(",\"type\":\"").append(escapeJson(meta.getExtension())).append("\"")
              .append("}");
        }
        sb.append("]");
    }

    /**
     * 从 Spring 应用上下文获取 FileMetadataManager Bean
     * <p>
     * 由于 ReActAgent 不是 Spring Bean，无法 @Autowired，通过静态工具类间接获取。
     */
    private FileMetadataManager getMetadataManager() {
        return SpringBeanUtils.getBean(FileMetadataManager.class);
    }

    /**
     * 简单 JSON 字符串转义，防止 SSE 解析中断
     */
    private String escapeJson(String s) {
        if (s == null) return "";
        return s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
