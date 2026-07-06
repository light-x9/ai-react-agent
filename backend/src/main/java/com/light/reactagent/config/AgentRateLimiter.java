package com.light.reactagent.config;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Agent 并发限流器
 * <p>
 * 两级限制：
 * 1. 全局并发上限 —— 防止整体过载烧钱（LLM 调用是计费资源）
 * 2. 每 key 并发上限 —— 同一客户端同时只能跑 1 个 Agent，防狂发请求
 * <p>
 * 当前 key 用客户端 IP（无认证阶段）；JWT 认证上线后改为 userId。
 * <p>
 * 注意：perKey 的 Semaphore 目前不会清理，IP 数量在无认证阶段可能增长，
 * 认证后改 userId 数量有限，问题不大；如需清理可后续加定时淘汰。
 */
@Component
public class AgentRateLimiter {

    /** 全局同时运行的 Agent 数上限 */
    private static final int MAX_GLOBAL = 5;

    /** 单个 key 同时运行的 Agent 数上限（同一用户/IP 同时只跑 1 个） */
    private static final int PER_KEY = 1;

    /** 获取许可的等待时间（秒），超时直接拒绝 */
    private static final long ACQUIRE_TIMEOUT = 3L;

    private final Semaphore global = new Semaphore(MAX_GLOBAL, true);
    private final ConcurrentHashMap<String, Semaphore> perKey = new ConcurrentHashMap<>();

    /**
     * 尝试获取运行许可
     *
     * @param key 客户端标识（当前为 IP，认证后为 userId）
     * @return true=获取成功需配套调用 release；false=并发已满应拒绝请求
     */
    public boolean tryAcquire(String key) {
        try {
            if (!global.tryAcquire(ACQUIRE_TIMEOUT, TimeUnit.SECONDS)) {
                return false;
            }
            Semaphore s = perKey.computeIfAbsent(key, k -> new Semaphore(PER_KEY, true));
            if (!s.tryAcquire(ACQUIRE_TIMEOUT, TimeUnit.SECONDS)) {
                global.release();
                return false;
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 释放运行许可（Agent 执行完毕后调用）
     */
    public void release(String key) {
        Semaphore s = perKey.get(key);
        if (s != null) {
            s.release();
        }
        global.release();
    }
}
