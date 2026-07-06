package com.light.reactagent.controller;

import com.light.reactagent.entity.Conversation;
import com.light.reactagent.entity.Message;
import com.light.reactagent.repository.ConversationRepository;
import com.light.reactagent.repository.MessageRepository;
import jakarta.annotation.Resource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 对话管理接口（多租户：按 userId 隔离）
 * <p>
 * 提供会话的创建、列表、删除、消息历史查询、消息保存。
 * 替代前端 localStorage 作为权威存储。
 */
@RestController
@RequestMapping("/conversation")
public class ConversationController {

    @Resource
    private ConversationRepository conversationRepository;

    @Resource
    private MessageRepository messageRepository;

    /**
     * 创建新会话
     */
    @PostMapping
    public Map<String, Object> create(@RequestBody CreateRequest req) {
        String userId = currentUserId();
        Conversation c = new Conversation();
        c.setUserId(userId);
        c.setTitle(req.title());
        conversationRepository.save(c);
        return Map.of(
                "success", true,
                "id", c.getId(),
                "title", c.getTitle()
        );
    }

    /**
     * 当前用户的会话列表（按更新时间倒序）
     */
    @GetMapping
    public Map<String, Object> list() {
        String userId = currentUserId();
        List<Conversation> list = conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        List<Map<String, Object>> items = list.stream().map(c -> Map.<String, Object>of(
                "id", c.getId(),
                "title", c.getTitle(),
                "updatedAt", c.getUpdatedAt().toString()
        )).toList();
        return Map.of("success", true, "sessions", items);
    }

    /**
     * 删除会话及其所有消息（校验归属）
     */
    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, Object> delete(@PathVariable Long id) {
        String userId = currentUserId();
        Conversation c = conversationRepository.findById(id).orElse(null);
        if (c == null || !userId.equals(c.getUserId())) {
            return Map.of("success", false, "message", "会话不存在或无权限");
        }
        messageRepository.deleteByConversationId(id);
        conversationRepository.delete(c);
        return Map.of("success", true);
    }

    /**
     * 获取会话的消息历史（校验归属）
     */
    @GetMapping("/{id}/messages")
    public Map<String, Object> messages(@PathVariable Long id) {
        String userId = currentUserId();
        Conversation c = conversationRepository.findById(id).orElse(null);
        if (c == null || !userId.equals(c.getUserId())) {
            return Map.of("success", false, "message", "会话不存在或无权限");
        }
        List<Message> msgs = messageRepository.findByConversationIdOrderByCreatedAtAsc(id);
        List<Map<String, Object>> items = msgs.stream().map(m -> Map.<String, Object>of(
                "id", m.getId(),
                "role", m.getRole(),
                "content", m.getContent(),
                "createdAt", m.getCreatedAt().toString()
        )).toList();
        return Map.of("success", true, "messages", items);
    }

    /**
     * 保存一条消息到会话（用户消息或 AI 回复）
     * 若是首条用户消息且标题为默认值，自动用消息内容更新标题
     */
    @PostMapping("/{id}/messages")
    public Map<String, Object> saveMessage(@PathVariable Long id, @RequestBody SaveMessageRequest req) {
        String userId = currentUserId();
        Conversation c = conversationRepository.findById(id).orElse(null);
        if (c == null || !userId.equals(c.getUserId())) {
            return Map.of("success", false, "message", "会话不存在或无权限");
        }
        Message m = new Message();
        m.setConversationId(id);
        m.setRole(req.role());
        m.setContent(req.content());
        messageRepository.save(m);

        // 首条用户消息更新标题
        if ("user".equals(req.role()) && "新对话".equals(c.getTitle())) {
            String title = generateTitle(req.content());
            c.setTitle(title);
        }
        conversationRepository.save(c); // 触发 @PreUpdate 更新 updatedAt

        return Map.of("success", true, "id", m.getId(), "title", c.getTitle());
    }

    /** 标题最大长度（超出时末尾加省略号） */
    private static final int TITLE_MAX_LEN = 20;

    /**
     * 从用户首条消息生成会话标题：完整保留原文，仅超长时从末尾截断
     * <p>
     * 不做任何前缀剥离，保证句子语义完整。
     */
    private String generateTitle(String content) {
        if (content == null || content.isBlank()) return "新对话";
        String title = content.trim();
        if (title.length() > TITLE_MAX_LEN) {
            title = title.substring(0, TITLE_MAX_LEN) + "…";
        }
        return title;
    }

    /**
     * 重命名会话标题
     */
    @PutMapping("/{id}")
    public Map<String, Object> rename(@PathVariable Long id, @RequestBody RenameRequest req) {
        String userId = currentUserId();
        Conversation c = conversationRepository.findById(id).orElse(null);
        if (c == null || !userId.equals(c.getUserId())) {
            return Map.of("success", false, "message", "会话不存在或无权限");
        }
        if (req.title() == null || req.title().isBlank()) {
            return Map.of("success", false, "message", "标题不能为空");
        }
        c.setTitle(req.title().trim());
        conversationRepository.save(c);
        return Map.of("success", true, "title", c.getTitle());
    }

    private String currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() != null
                && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        throw new IllegalStateException("未认证");
    }

    public record CreateRequest(String title) {
    }

    public record SaveMessageRequest(String role, String content) {
    }

    public record RenameRequest(String title) {
    }
}
