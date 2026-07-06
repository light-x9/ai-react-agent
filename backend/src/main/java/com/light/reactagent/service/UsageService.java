package com.light.reactagent.service;

import com.light.reactagent.entity.UsageRecord;
import com.light.reactagent.repository.UsageRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

/**
 * 用量额度服务
 * <p>
 * 用原子 UPDATE 计数（WHERE count < limit），禁用先查后增防并发 race。
 * increment 返回 0 时：可能是记录不存在，尝试 insert；若并发冲突再 increment 一次。
 */
@Service
@Slf4j
public class UsageService {

    private final UsageRecordRepository usageRecordRepository;

    @Value("${lightmanus.quota.daily-chat:100}")
    private int dailyChatLimit;

    @Value("${lightmanus.quota.daily-web-search:30}")
    private int dailyWebSearchLimit;

    public UsageService(UsageRecordRepository usageRecordRepository) {
        this.usageRecordRepository = usageRecordRepository;
    }

    /**
     * 检查并增加对话计数，返回是否允许继续
     */
    @Transactional
    public boolean checkAndIncrementChat(String userId) {
        return checkAndIncrement(userId, true);
    }

    /**
     * 检查并增加联网搜索计数，返回是否允许继续
     */
    @Transactional
    public boolean checkAndIncrementWebSearch(String userId) {
        return checkAndIncrement(userId, false);
    }

    private boolean checkAndIncrement(String userId, boolean isChat) {
        if (userId == null) {
            return true; // 无用户标识时不限额（兼容场景）
        }
        LocalDate today = LocalDate.now();
        int limit = isChat ? dailyChatLimit : dailyWebSearchLimit;

        // 1. 先尝试原子 increment（仅当 count < limit 才 +1）
        int updated = isChat
                ? usageRecordRepository.incrementChat(userId, today, limit)
                : usageRecordRepository.incrementWebSearch(userId, today, limit);
        if (updated > 0) {
            return true;
        }

        // 2. increment 返回 0：记录不存在 或 已达上限。尝试 insert 新记录
        try {
            UsageRecord record = new UsageRecord();
            record.setUserId(userId);
            record.setUsageDate(today);
            record.setChatCount(isChat ? 1 : 0);
            record.setWebSearchCount(isChat ? 0 : 1);
            usageRecordRepository.saveAndFlush(record);
            return true;
        } catch (DataIntegrityViolationException e) {
            // 3. 并发冲突：记录已被另一线程插入，再 increment 一次
            updated = isChat
                    ? usageRecordRepository.incrementChat(userId, today, limit)
                    : usageRecordRepository.incrementWebSearch(userId, today, limit);
            return updated > 0;
        }
    }

    /**
     * 查询今日用量
     */
    public Map<String, Object> getTodayUsage(String userId) {
        LocalDate today = LocalDate.now();
        UsageRecord record = usageRecordRepository.findByUserIdAndUsageDate(userId, today).orElse(null);
        int chatUsed = record != null ? record.getChatCount() : 0;
        int searchUsed = record != null ? record.getWebSearchCount() : 0;
        return Map.of(
                "chatUsed", chatUsed,
                "chatLimit", dailyChatLimit,
                "searchUsed", searchUsed,
                "searchLimit", dailyWebSearchLimit
        );
    }
}
