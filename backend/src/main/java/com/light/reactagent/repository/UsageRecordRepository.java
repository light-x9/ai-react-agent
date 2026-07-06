package com.light.reactagent.repository;

import com.light.reactagent.entity.UsageRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface UsageRecordRepository extends JpaRepository<UsageRecord, Long> {

    Optional<UsageRecord> findByUserIdAndUsageDate(String userId, LocalDate usageDate);

    /**
     * 原子自增对话计数（仅当未达上限时 +1），返回受影响行数
     */
    @Modifying
    @Query("UPDATE UsageRecord u SET u.chatCount = u.chatCount + 1 " +
            "WHERE u.userId = :uid AND u.usageDate = :d AND u.chatCount < :lim")
    int incrementChat(@Param("uid") String userId, @Param("d") LocalDate date, @Param("lim") int limit);

    /**
     * 原子自增联网搜索计数（仅当未达上限时 +1），返回受影响行数
     */
    @Modifying
    @Query("UPDATE UsageRecord u SET u.webSearchCount = u.webSearchCount + 1 " +
            "WHERE u.userId = :uid AND u.usageDate = :d AND u.webSearchCount < :lim")
    int incrementWebSearch(@Param("uid") String userId, @Param("d") LocalDate date, @Param("lim") int limit);
}
