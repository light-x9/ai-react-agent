package com.light.reactagent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.time.LocalDate;

/**
 * 用户每日用量记录（按 userId + usageDate 唯一）
 */
@Entity
@Table(name = "usage_records",
        uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "usageDate"}))
@Data
public class UsageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String userId;

    @Column(nullable = false)
    private LocalDate usageDate;

    @Column(nullable = false)
    private int chatCount = 0;

    @Column(nullable = false)
    private int webSearchCount = 0;
}
