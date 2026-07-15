package com.light.reactagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 多模型路由配置绑定前缀 spring.ai.router.*。
 * <p>
 * 示例：
 * <pre>
 * spring.ai.router:
 *   models:
 *     - name: deepseek
 *       primary: true
 *       model-id: deepseek-chat
 *       provider: openai
 *     - name: qwen
 *       model-id: qwen3-32b
 *       provider: dashscope
 *     - name: ollama
 *       model-id: gemma3:1b
 *       provider: ollama
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "spring.ai.router")
public class MultiModelProperties {

    /** 模型列表，未配置时使用主 openai 兜底 */
    private List<ModelDef> models = new ArrayList<>();

    /** 健康检查 */
    private Health health = new Health();

    @Data
    public static class ModelDef {
        /** 模型标识（唯一 key），例如 deepseek / qwen / ollama */
        private String name;
        /** 是否首选 */
        private boolean primary = false;
        /** Spring AI model bean 对应的 modelId（spring.ai.openai.chat.options.model） */
        private String modelId;
        /** 提供者：openai / dashscope / ollama */
        private String provider = "openai";
        /** 短描述，前端展示用 */
        private String description;
        /** 单次调用成本权重（用于成本优化路由） */
        private double costWeight = 1.0;
        /** 超时秒数 */
        private int timeoutSeconds = 60;
        /** 是否启用 */
        private boolean enabled = true;
    }

    @Data
    public static class Health {
        /** 健康检查周期（毫秒） */
        private long checkIntervalMs = 30_000;
        /** 连续失败多少次后熔断 */
        private int failureThreshold = 3;
        /** 熔断恢复等待时间（毫秒） */
        private long resetTimeoutMs = 120_000;
        /** 是否启用健康检查 */
        private boolean enabled = true;
    }
}
