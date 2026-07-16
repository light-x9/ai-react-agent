package com.light.reactagent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体
 * <p>
 * 对应数据库 users 表，存储注册用户的账号、密码哈希、昵称等信息。
 * 密码使用 BCrypt 加密存储，绝不保存明文。
 */
@Entity
@Table(name = "users")
@Data
public class User {

    /** 主键 ID，自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 用户名，唯一，用于登录 */
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** BCrypt 哈希后的密码，不存明文 */
    @Column(nullable = false)
    private String password;

    /** 昵称，默认与用户名相同 */
    @Column(length = 100)
    private String nickname;

    /** 头像 URL，为空则展示默认头像 */
    @Column(length = 500)
    private String avatar;

    /** 个人简介 */
    @Column(length = 300)
    private String bio;

    /** 注册时间，由 @PrePersist 自动填充 */
    private LocalDateTime createdAt;

    /**
     * 入库前自动回调：
     * - 若 createdAt 未赋值，取当前时间
     * - 若 nickname 未赋值，用 username 兜底
     */
    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.nickname == null) {
            this.nickname = this.username;
        }
    }
}
