package com.light.reactagent.router;

import lombok.Data;
import org.springframework.ai.chat.model.ChatModel;

/**
 * 单个模型路由条目。
 */
@Data
public class RouteModel {
    /** 模型标识 */
    private final String name;
    /** 底层 ChatModel 实例 */
    private final ChatModel chatModel;
    /** 是否首选 */
    private final boolean primary;
    /** 成本权重（越小越优先） */
    private final double costWeight;
    /** 描述 */
    private final String description;

    // ---- 运行时健康状态（非 final，路由过程中动态更新） ----
    private volatile boolean circuitOpen = false;
    private volatile long lastFailureAt = 0L;
    private volatile int consecutiveFailures = 0;

    public RouteModel(String name, ChatModel chatModel, boolean primary, double costWeight, String description) {
        this.name = name;
        this.chatModel = chatModel;
        this.primary = primary;
        this.costWeight = costWeight;
        this.description = description;
    }
}
