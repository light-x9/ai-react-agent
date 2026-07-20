package com.light.reactagent.config;

import io.modelcontextprotocol.client.McpClient.SyncSpec;
import org.springframework.ai.mcp.customizer.McpSyncClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * MCP 客户端配置 —— 覆盖默认的 20 秒初始化超时
 * <p>
 * 默认超时太短：Spring Boot 版的 image-search MCP server 启动就要十几秒，
 * npx 版的高德地图 MCP 需要启动 Node 进程，都需要更长超时。
 */
@Configuration
public class McpClientConfig {

    /** MCP 初始化超时：60 秒 */
    private static final Duration MCP_TIMEOUT = Duration.ofSeconds(60);

    /**
     * 自定义 McpSyncClientCustomizer，覆盖超时时间
     * 注入到 Spring AI 的自动配置中，替换默认行为
     */
    @Bean
    public McpSyncClientCustomizer mcpSyncClientCustomizer() {
        return (name, builder) -> builder.requestTimeout(MCP_TIMEOUT);
    }
}
