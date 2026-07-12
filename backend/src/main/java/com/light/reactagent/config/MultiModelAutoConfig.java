package com.light.reactagent.config;

import com.light.reactagent.router.MultiModelRouter;
import com.light.reactagent.router.RouteModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;

import java.util.ArrayList;
import java.util.List;

/**
 * 多模型路由自动装配。
 * <p>
 * 读取 spring.ai.router.models 配置，为每个模型构造一个内部 ChatModel 实例，
 * 然后用 MultiModelRouter 兜底注入。AI 只要 @Resource ChatModel xxx 拿到的就是 Router。
 *
 * 路由算法：
 * 1) 查会话级锁定（SessionContextHolder 中锁了哪个就走哪个）
 * 2) 未锁定 → 取 primary=true 的首选
 * 3) 如果首选处于熔断 → 按 priority 找下一个健康的
 * 4) 全部熔断 → 取熔断已过 resetTimeout 的抢救一个，否则抛异常
 */
@AutoConfiguration
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "spring.ai.router.models[0].name", matchIfMissing = false)
public class MultiModelAutoConfig {

    private static final Logger log = LoggerFactory.getLogger(MultiModelAutoConfig.class);

    /**
     * 多模型路由器：作为唯一的 @Primary ChatModel 注出。所有 @Resource ChatModel xxx 自动拿到路由。
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "multiModelRouter")
    public ChatModel multiModelRouter(ObjectProvider<MultiModelProperties> propsProvider) {
        MultiModelProperties props = propsProvider.getIfAvailable();
        if (props == null || props.getModels().isEmpty()) {
            return null;
        }
        List<RouteModel> routes = new ArrayList<>();
        for (MultiModelProperties.ModelDef def : props.getModels()) {
            if (!def.isEnabled()) continue;
            try {
                ChatModel chatModel = buildChatModel(def);
                RouteModel route = new RouteModel(def.getName(), chatModel, def.isPrimary(), def.getCostWeight(), def.getDescription());
                routes.add(route);
                log.info("[MultiModelRouter] 注册模型 route: {} (provider={}, primary={})", def.getName(), def.getProvider(), def.isPrimary());
            } catch (Exception e) {
                log.error("[MultiModelRouter] 构造模型 {} 失败，跳过此 route: {}", def.getName(), e.getMessage(), e);
            }
        }
        if (routes.isEmpty()) {
            log.warn("[MultiModelRouter] 没有可用的模型路由，返回 null（依赖原有 ChatModel 兜底）");
            return null;
        }

        return new MultiModelRouter(routes, props.getHealth().getFailureThreshold(),
                props.getHealth().getResetTimeoutMs(), props.getHealth().isEnabled());
    }

    private ChatModel buildChatModel(MultiModelProperties.ModelDef def) {
        String provider = def.getProvider().toLowerCase();
        switch (provider) {
            case "openai":
            case "deepseek":
                return OpenAiChatModel.builder()
                        .openAiApi(OpenAiApi.builder()
                                .apiKey(resolveEnv("DEEPSEEK_API_KEY", ""))
                                .baseUrl(resolveEnv("DEEPSEEK_BASE_URL", "https://api.deepseek.com"))
                                .build())
                        .defaultOptions(org.springframework.ai.openai.OpenAiChatOptions.builder()
                                .model(def.getModelId())
                                .build())
                        .build();

            case "ollama":
                return OllamaChatModel.builder()
                        .ollamaApi(OllamaApi.builder()
                                .baseUrl(resolveEnv("OLLAMA_BASE_URL", "http://localhost:11434"))
                                .build())
                        .defaultOptions(OllamaOptions.builder()
                                .model(def.getModelId())
                                .build())
                        .build();

            default:
                log.warn("[MultiModelRouter] 未知 provider {}，降级用 spring-ai 默认 bean 查找", provider);
                throw new IllegalArgumentException("Unsupported provider: " + provider);
        }
    }

    /** 环境变量安全读取工具（兼容 Java 21 getenv + 默认值） */
    private static String resolveEnv(String key, String defaultValue) {
        String val = System.getenv(key);
        return (val != null && !val.isBlank()) ? val : defaultValue;
    }
}
