package com.light.reactagent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户画像（Persona）—— 个人知识引擎的核心表。
 * <p>
 * 每轮对话结束后异步抽取摘要，沉淀用户的偏好、技术栈、常接触领域、写作风格等。
 * 下次对话开始时把这些画像注入 system prompt，让 AI 越用越懂用户。
 * <p>
 * 主键是 userId（与 users 表一对一），不重复建 id 列。
 */
@Entity
@Table(name = "persona_profile")
@Data
public class PersonaProfile {

    /** 用户 ID，与 users 表主键一一对应 */
    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 用户昵称（冗余字段，避免每次联表查 users） */
    @Column(length = 100)
    private String nickname;

    /** 偏好语言：zh / en / … */
    @Column(length = 10)
    private String preferredLanguage;

    /** 技术栈 / 常用工具，逗号分隔：Python,Vue,Spring Boot */
    @Column(name = "tech_stack", length = 500)
    private String techStack;

    /** 常接触领域 / 兴趣话题，逗号分隔：AI,后端开发,数据分析 */
    @Column(name = "interests", length = 500)
    private String interests;

    /** 写作风格：concise / detailed / humorous / academic / … */
    @Column(name = "writing_style", length = 50)
    private String writingStyle;

    /** 最近 N 次对话的话题摘要（JSON 数组字符串，最多保留 20 条） */
    @Column(name = "recent_topics", columnDefinition = "TEXT")
    private String recentTopics;

    /** 累计对话轮次 */
    @Column(name = "conversation_count", nullable = false)
    private Integer conversationCount = 0;

    /** 最近一次对话时间 */
    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;

    /** 画像最近更新时间（异步抽取摘要后更新） */
    @Column(name = "profile_updated_at")
    private LocalDateTime profileUpdatedAt;

    /** 创建时间 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 乐观锁版本号 */
    @Column(name = "version", nullable = false)
    private Long version = 0L;
}
