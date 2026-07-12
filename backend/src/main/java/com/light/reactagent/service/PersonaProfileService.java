package com.light.reactagent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.light.reactagent.entity.PersonaProfile;
import com.light.reactagent.repository.PersonaProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用户画像（Persona）服务 —— 个人知识引擎的脑库。
 * <p>
 * 职责：
 * 1. 每轮对话前：把画像压缩成一段「用户是谁」的 system context 喂给 LLM。
 * 2. 每轮对话后：异步调 LLM 从对话摘要里抽取偏好 / 话题增量，合并到画像表。
 * 3. 给前端提供「继续上次 / 你可能想继续」的话题建议。
 * <p>
 * 持久化极简方式：把 techStack/interests 用逗号分隔存在 varchar 里；recent_topics 用 JSON 数组存在 TEXT 里。
 */
@Slf4j
@Service
public class PersonaProfileService {

    /** 每个用户最多保留的最近话题条数 */
    private static final int MAX_RECENT_TOPICS = 20;
    /** 每个用户保留的技术栈 / 兴趣条目上限 */
    private static final int MAX_TAG_ITEMS = 30;
    /** 透传排除符：LLM 返回「无变化 / none / 无新增」等视为无更新 */
    private static final Pattern NO_UPDATE_PATTERN = Pattern.compile("^(无|none|no\\s*change|无变化|无新增|无需更新|no\\s*update)$", Pattern.CASE_INSENSITIVE);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 用于异步抽取摘要的专用 system prompt（独立下发，不污染主对话上下文） */
    private static final String SUMMARY_SYSTEM = "你是用户画像分析助手。根据本轮对话片段，提取以下字段。\n"
            + "1) tech_stack: 用户使用的技术栈 / 工具链（逗号分隔，仅提取明确提到的，不要猜测）\n"
            + "2) interests: 用户本次关心的话题 / 领域（逗号分隔）\n"
            + "3) writing_style: 用户写作 / 表达风格（一个词：concise / detailed / humorous / academic / neutral）\n"
            + "4) language: 用户本次使用的主要语言（zh / en）\n"
            + "5) topic_summary: 本轮对话核心话题的一句中文摘要（不超过40字）\n"
            + "只输出 JSON，不要任何解释。格式：{\"tech_stack\":\"...\",\"interests\":\"...\",\"writing_style\":\"...\",\"language\":\"...\",\"topic_summary\":\"...\"}\n"
            + "如果某字段无法识别，留空字符串。";

    private final PersonaProfileRepository profileRepo;
    private final ChatModel chatModel;

    public PersonaProfileService(PersonaProfileRepository profileRepo, ChatModel chatModel) {
        this.profileRepo = profileRepo;
        this.chatModel = chatModel;
    }

    // ==========================================
    //  画像读写
    // ==========================================

    /**
     * 取用户画像（不存在时懒建一个默认的）。
     */
    @Transactional
    public PersonaProfile getOrCreate(Long userId, String nickname) {
        return profileRepo.findByUserId(userId).orElseGet(() -> {
            PersonaProfile p = new PersonaProfile();
            p.setUserId(userId);
            p.setNickname(nickname);
            p.setPreferredLanguage("zh");
            p.setWritingStyle("neutral");
            p.setConversationCount(0);
            p.setCreatedAt(LocalDateTime.now());
            p.setVersion(0L);
            try {
                p.setRecentTopics(MAPPER.writeValueAsString(new ArrayList<>()));
            } catch (Exception e) {
                p.setRecentTopics("[]");
            }
            profileRepo.save(p);
            return p;
        });
    }

    /**
     * 读取用户画像的 system context 片段，给 LLM 作为「用户是谁」的提示。
     * <p>
     * 这个片段会作为 SystemMessage 拼到 prompt 头部，所以越精简越好。
     */
    public String buildSystemContext(Long userId, String nickname) {
        if (userId == null) return "";
        PersonaProfile p = getOrCreate(userId, nickname);
        StringBuilder sb = new StringBuilder();
        sb.append("## 关于当前用户的记忆（请据此调整表达方式和深度）\n");
        if (p.getPreferredLanguage() != null && !p.getPreferredLanguage().isBlank()) {
            sb.append("- 偏好语言：").append(p.getPreferredLanguage()).append("\n");
        }
        if (p.getWritingStyle() != null && !p.getWritingStyle().isBlank()) {
            sb.append("- 表达风格：").append(p.getWritingStyle()).append("\n");
        }
        if (p.getTechStack() != null && !p.getTechStack().isBlank()) {
            sb.append("- 技术栈 / 常用工具：").append(p.getTechStack()).append("\n");
        }
        if (p.getInterests() != null && !p.getInterests().isBlank()) {
            sb.append("- 常关心的话题：").append(p.getInterests()).append("\n");
        }
        if (p.getLastActiveAt() != null) {
            sb.append("- 上次对话时间：").append(p.getLastActiveAt().toLocalDate()).append("\n");
        }
        List<String> recentTopics = parseRecentTopics(p.getRecentTopics());
        if (!recentTopics.isEmpty()) {
            sb.append("- 最近关心的话题：");
            int max = Math.min(5, recentTopics.size());
            for (int i = 0; i < max; i++) {
                if (i > 0) sb.append("；");
                sb.append(recentTopics.get(recentTopics.size() - 1 - i));
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * 记录一次对话（次数 +1，lastActiveAt = now）。
     * 在对话开始时调用。
     */
    @Transactional
    public void touchConversation(Long userId, String nickname) {
        if (userId == null) return;
        PersonaProfile p = getOrCreate(userId, nickname);
        p.setConversationCount((p.getConversationCount() == null ? 0 : p.getConversationCount()) + 1);
        p.setNickname(nickname);
        p.setLastActiveAt(LocalDateTime.now());
        profileRepo.save(p);
    }

    /**
     * 异步抽取本轮对话摘要 + 合并到画像表。
     * <p>
     * 关键设计：
     * - 异步，不阻塞主流程
     * - 用独立的 ChatModel 单轮调用（不走流式，节省资源）
     * - techStack / interests 增量 merge（不覆盖），保持「越用越厚」
     * - recent_topics append 前 20
     */
    @Async
    @Transactional
    public void afterConversationAsync(Long userId, String nickname,
                                       String userMessage, String assistantMessage) {
        if (userId == null) return;
        try {
            // 1) 拉现有画像
            PersonaProfile p = getOrCreate(userId, nickname);

            // 2) 调 LLM 抽取增量
            Map<String, String> extracted = extractViaLlm(userMessage, assistantMessage);

            // 3) merge 增量
            boolean changed = false;

            String newTech = extracted.getOrDefault("tech_stack", "");
            if (shouldUpdate(newTech)) {
                p.setTechStack(mergeTags(p.getTechStack(), newTech));
                changed = true;
            }

            String newInterests = extracted.getOrDefault("interests", "");
            if (shouldUpdate(newInterests)) {
                p.setInterests(mergeTags(p.getInterests(), newInterests));
                changed = true;
            }

            String newStyle = extracted.getOrDefault("writing_style", "");
            if (shouldUpdate(newStyle) && !newStyle.equalsIgnoreCase(p.getWritingStyle())) {
                p.setWritingStyle(newStyle.toLowerCase());
                changed = true;
            }

            String newLang = extracted.getOrDefault("language", "");
            if (shouldUpdate(newLang) && !newLang.equalsIgnoreCase(p.getPreferredLanguage())) {
                p.setPreferredLanguage(newLang.toLowerCase());
                changed = true;
            }

            String topicSummary = extracted.getOrDefault("topic_summary", "");
            if (shouldUpdate(topicSummary)) {
                appendTopic(p, topicSummary);
                changed = true;
            }

            if (changed) {
                p.setProfileUpdatedAt(LocalDateTime.now());
                p.setVersion((p.getVersion() == null ? 0L : p.getVersion()) + 1);
                profileRepo.save(p);
                log.debug("[Persona] 画像已更新 userId={}, topics={}", userId, topicSummary);
            }
        } catch (Exception e) {
            // 画像更新失败绝对不能影响主流程
            log.warn("[Persona] 异步更新画像失败 userId={}, err={}", userId, e.getMessage());
        }
    }

    // ==========================================
    // 内部 helpers
    // ==========================================

    /**
     * 用 LLM 从对话片段里提取画像增量。
     * 返回的是 k,v 字符串 map，解析失败时返回空 map。
     */
    private Map<String, String> extractViaLlm(String userMessage, String assistantMessage) {
        try {
            String content = "用户提问：" + truncate(userMessage, 800)
                    + "\n\nAI回答摘要：" + truncate(assistantMessage, 800);
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(SUMMARY_SYSTEM),
                    new UserMessage(content)
            ));
            ChatResponse resp = chatModel.call(prompt);
            String raw = resp.getResult().getOutput().getText();
            return parseJsonLike(raw);
        } catch (Exception e) {
            log.warn("[Persona] LLM 抽取失败（不影响主流程）：{}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * 解析 LLM 输出的 JSON（容错：尝试剥掉 markdown code fence、中文引号等）。
     */
    private Map<String, String> parseJsonLike(String raw) {
        if (raw == null || raw.isBlank()) return new HashMap<>();
        String s = raw.strip();
        // 剥掉 ```json … ``` fence
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            int lastFence = s.lastIndexOf("```");
            if (nl > 0 && lastFence > nl) {
                s = s.substring(nl + 1, lastFence).strip();
            }
        }
        s = s.replace("\u201C", "\"").replace("\u201D", "\"").replace("\u2018", "'").replace("\u2019", "'");
        try {
            TypeReference<HashMap<String, String>> ref = new TypeReference<>() {
            };
            HashMap<String, String> m = MAPPER.readValue(s, ref);
            return m == null ? new HashMap<>() : m;
        } catch (Exception e) {
            // JSON 不合规时，尝试通过正则兜底提取 topic_summary（最常见的关键字段）
            log.debug("[Persona] JSON 解析失败，尝试正则兜底：{}", e.getMessage());
            Map<String, String> fallback = new HashMap<>();
            Matcher tm = Pattern.compile("\"topic_summary\"\\s*[:：]\\s*\"([^\"]+)\"").matcher(s);
            if (tm.find()) fallback.put("topic_summary", tm.group(1).strip());
            return fallback;
        }
    }

    /**
     * merge 标签：旧值 field + 新值 newTags（逗号分隔），去重后截断到上限。
     */
    private String mergeTags(String existing, String newTags) {
        Set<String> set = new LinkedHashSet<>();
        if (existing != null && !existing.isBlank()) {
            for (String t : existing.split(",")) {
                String tt = t.strip();
                if (!tt.isEmpty()) set.add(tt.toLowerCase());
            }
        }
        if (newTags != null && !newTags.isBlank()) {
            for (String t : newTags.split("[,，]")) {
                String tt = t.strip();
                if (!tt.isEmpty()) set.add(tt.toLowerCase());
            }
        }
        List<String> list = new ArrayList<>(set);
        if (list.size() > MAX_TAG_ITEMS) {
            list = list.subList(list.size() - MAX_TAG_ITEMS, list.size());
        }
        return String.join(",", list);
    }

    /**
     * 向 recent_topics JSON 数组头部追加一条新摘要。
     */
    private void appendTopic(PersonaProfile p, String topic) throws Exception {
        List<String> list = parseRecentTopics(p.getRecentTopics());
        list.add(topic);
        while (list.size() > MAX_RECENT_TOPICS) list.remove(0);
        p.setRecentTopics(MAPPER.writeValueAsString(list));
    }

    private List<String> parseRecentTopics(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            List<String> list = MAPPER.readValue(json, new TypeReference<ArrayList<String>>() {
            });
            return list == null ? new ArrayList<>() : list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private boolean shouldUpdate(String value) {
        if (value == null || value.isBlank()) return false;
        String v = value.strip().replaceAll("\s+", " ");
        return !NO_UPDATE_PATTERN.matcher(v).matches();
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
