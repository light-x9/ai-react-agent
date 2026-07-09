package com.light.reactagent.chatmemory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 基于文件的对话记忆（ChatMemory 实现）。
 *
 * 职责：维护「喂给 LLM 的精简对话历史」，与 DB 中用于展示的完整历史（Conversation/Message）职责分离。
 *
 * 关键能力：
 * 1. 滑动窗口：始终保留最近 maxMessages 条消息，超出部分触发压缩。
 * 2. 摘要压缩：超出窗口时，调用大模型把最老的 summarizeCount 条合并为一条摘要消息，
 *    摘要以特殊标记前缀存储，可跨多轮增量累积（每次只压缩新滑出窗口的部分）。
 * 3. 多租户隔离：以 conversationId（本项目中为 userId + ":" + chatId）为键，文件互不可见。
 *
 * 存储格式：JSON 数组 [{role, content}]，role ∈ {user, assistant, summary}。
 * 选用 JSON（而非 Kryo 直接序列化 Spring AI Message）是为了避免 Message 子类序列化脆弱、且可读便于排查。
 */
@Slf4j
public class FileBasedChatMemory implements ChatMemory {

    /** 摘要消息的内容前缀标记，用于识别/重建摘要消息 */
    private static final String SUMMARY_MARKER = "<<CONVERSATION_SUMMARY>>";

    private final String baseDir;
    private final ChatModel summaryModel;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final int maxMessages;
    private final int summarizeCount;
    /** 每条消息字符上限，超过则在落盘前截断，避免单条超长把窗口撑爆 */
    private final int maxSingleMessageChars;

    private final ConcurrentMap<String, Object> locks = new ConcurrentHashMap<>();

    public FileBasedChatMemory(String dir, ChatModel summaryModel,
                              int maxMessages, int summarizeCount, int maxSingleMessageChars) {
        this.baseDir = dir;
        this.summaryModel = summaryModel;
        this.maxMessages = maxMessages;
        this.summarizeCount = summarizeCount;
        this.maxSingleMessageChars = maxSingleMessageChars;
        File dirFile = new File(dir);
        if (!dirFile.exists()) {
            dirFile.mkdirs();
        }
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        synchronized (lockFor(conversationId)) {
            List<Message> conv = load(conversationId);
            for (Message m : messages) {
                conv.add(clamp(m));
            }
            conv = maybeSummarize(conv);
            save(conversationId, conv);
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        synchronized (lockFor(conversationId)) {
            // 返回拷贝，避免调用方（Agent 运行期）的修改污染存储对象
            return new ArrayList<>(load(conversationId));
        }
    }

    @Override
    public void clear(String conversationId) {
        synchronized (lockFor(conversationId)) {
            File file = fileFor(conversationId);
            if (file.exists()) {
                file.delete();
            }
        }
    }

    // ===================== 内部存储 =====================

    private List<Message> load(String conversationId) {
        File file = fileFor(conversationId);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        try {
            String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            List<StoredMessage> list = objectMapper.readValue(json, new TypeReference<List<StoredMessage>>() {});
            List<Message> messages = new ArrayList<>();
            for (StoredMessage sm : list) {
                String content = sm.content == null ? "" : sm.content;
                if ("summary".equals(sm.role)) {
                    messages.add(new UserMessage(SUMMARY_MARKER + "\n" + content));
                } else if ("assistant".equals(sm.role)) {
                    messages.add(new AssistantMessage(content));
                } else {
                    messages.add(new UserMessage(content));
                }
            }
            return messages;
        } catch (Exception e) {
            log.warn("读取会话记忆文件失败 conversationId={}: {}", conversationId, e.toString());
            return new ArrayList<>();
        }
    }

    private void save(String conversationId, List<Message> conv) {
        try {
            List<StoredMessage> list = new ArrayList<>();
            for (Message m : conv) {
                String role;
                String content = m.getText() == null ? "" : m.getText();
                if (isSummary(m)) {
                    role = "summary";
                    content = stripMarker(content);
                } else if (m instanceof AssistantMessage) {
                    role = "assistant";
                } else {
                    role = "user";
                }
                list.add(new StoredMessage(role, content));
            }
            String json = objectMapper.writeValueAsString(list);
            Files.write(fileFor(conversationId).toPath(), json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("写入会话记忆文件失败 conversationId={}: {}", conversationId, e.toString());
        }
    }

    /**
     * 滑动窗口 + 摘要压缩。
     * 保证列表前端始终至多一条摘要消息；超窗口时把最老 summarizeCount 条（摘要之后）压成一条摘要。
     */
    private List<Message> maybeSummarize(List<Message> conv) {
        int summaryIdx = -1;
        for (int i = 0; i < conv.size(); i++) {
            if (isSummary(conv.get(i))) {
                summaryIdx = i;
                break;
            }
        }
        int guard = 0;
        while (conv.size() > maxMessages && guard++ < 50) {
            int from = (summaryIdx >= 0) ? summaryIdx + 1 : 0;
            int to = Math.min(from + summarizeCount, conv.size());
            if (from >= to) {
                break;
            }
            List<Message> chunk = new ArrayList<>(conv.subList(from, to));
            String prevSummary = summaryIdx >= 0 ? stripMarker(conv.get(summaryIdx).getText()) : null;
            String summary = summarizeChunk(chunk, prevSummary);
            Message summaryMsg = new UserMessage(SUMMARY_MARKER + "\n" + (summary == null ? "" : summary));
            if (summaryIdx >= 0) {
                conv.set(summaryIdx, summaryMsg);
            } else {
                conv.add(0, summaryMsg);
                summaryIdx = 0;
            }
            conv.removeAll(chunk);
        }
        return conv;
    }

    /**
     * 调用大模型对一段对话做增量摘要。失败时回退到已有摘要，不丢上下文。
     */
    private String summarizeChunk(List<Message> chunk, String prevSummary) {
        if (summaryModel == null) {
            return prevSummary;
        }
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("你是一个 AI 助手的对话摘要器。请总结下面这段对话片段，")
              .append("使用与用户相同的语言。必须保留：用户的核心诉求与目标、关键事实与数据、")
              .append("工具检索到的关键结果（如找到的链接、生成的文件名）、以及任何已做出的决定。不要编造信息。\n\n");
            if (prevSummary != null && !prevSummary.isBlank()) {
                sb.append("=== 已有摘要（请在其基础上扩展，未变化的部分不要重复） ===\n")
                  .append(prevSummary).append("\n=== 已有摘要结束 ===\n\n");
            }
            sb.append("=== 待摘要对话片段 ===\n");
            for (Message m : chunk) {
                String role = (m instanceof AssistantMessage) ? "Assistant" : "User";
                sb.append(role).append(": ").append(m.getText()).append("\n");
            }
            sb.append("=== 片段结束 ===\n\n只输出更新后的摘要文本。");

            String text = summaryModel.call(new Prompt(sb.toString())).getResult().getOutput().getText();
            return text == null ? (prevSummary == null ? "" : prevSummary) : text.trim();
        } catch (Exception e) {
            log.warn("对话摘要生成失败：{}", e.toString());
            return prevSummary;
        }
    }

    private boolean isSummary(Message m) {
        return m.getText() != null && m.getText().startsWith(SUMMARY_MARKER);
    }

    private String stripMarker(String content) {
        if (content == null) {
            return "";
        }
        if (content.startsWith(SUMMARY_MARKER)) {
            return content.substring(SUMMARY_MARKER.length()).replaceFirst("^\\n", "");
        }
        return content;
    }

    /** 单条消息超长时截断，避免一条超大消息把窗口撑爆 */
    private Message clamp(Message m) {
        String content = m.getText();
        if (content != null && content.length() > maxSingleMessageChars) {
            String trimmed = content.substring(0, maxSingleMessageChars)
                    + "\n...[内容过长已截断，完整内容见历史记录]";
            if (m instanceof AssistantMessage) {
                return new AssistantMessage(trimmed);
            }
            return new UserMessage(trimmed);
        }
        return m;
    }

    private Object lockFor(String id) {
        return locks.computeIfAbsent(id, k -> new Object());
    }

    private File fileFor(String id) {
        // 过滤非法文件名字符，防目录穿越
        String safe = id.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        return new File(baseDir, safe + ".json");
    }

    /** JSON 落盘的轻量 DTO，仅记录角色与文本，规避 Message 子类序列化问题 */
    public static class StoredMessage {
        public String role;
        public String content;

        public StoredMessage() {
        }

        public StoredMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}
