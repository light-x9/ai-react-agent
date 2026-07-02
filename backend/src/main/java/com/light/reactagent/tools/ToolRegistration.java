package com.light.reactagent.tools;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 集中的工具注册类
 * 负责创建所有本地工具实例，并合并 MCP 外部工具，供 SuperAgent 使用
 */
@Configuration
public class ToolRegistration {

    @Value("${search-api.api-key}")
    private String searchApiKey;

    // 使用 ObjectProvider 延迟获取 MCP 工具，避免 MCP 服务不可用时阻塞启动
    private final ObjectProvider<ToolCallbackProvider> toolCallbackProvider;

    /**
     * RAG 知识库检索工具（Spring 自动注入）
     * 智能体可通过此工具从向量知识库中检索恋爱/情感相关的专业知识
     */
    private final RagSearchTool ragSearchTool;

    public ToolRegistration(ObjectProvider<ToolCallbackProvider> toolCallbackProvider,
                            RagSearchTool ragSearchTool) {
        this.toolCallbackProvider = toolCallbackProvider;
        this.ragSearchTool = ragSearchTool;
    }

    /**
     * 本地工具集：包含文件操作、网页搜索、PDF生成、RAG检索等
     */
    @Bean
    public ToolCallback[] allTools() {
        FileOperationTool fileOperationTool = new FileOperationTool();
        WebSearchTool webSearchTool = new WebSearchTool(searchApiKey);
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        ResourceDownloadTool resourceDownloadTool = new ResourceDownloadTool();
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool();
        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();
        TerminateTool terminateTool = new TerminateTool();
        return ToolCallbacks.from(
                fileOperationTool,
                webSearchTool,
                webScrapingTool,
                resourceDownloadTool,
                terminalOperationTool,
                pdfGenerationTool,
                terminateTool,
                ragSearchTool   // RAG 知识库检索工具，供智能体按需调用
        );
    }

    // 合并本地工具 + MCP 工具，供 SuperAgent（YuManus）使用
    // 如果 MCP 未配置或不可用，自动降级为仅本地工具
    @Bean
    public ToolCallback[] allToolsWithMcp() {
        ToolCallback[] localTools = allTools();
        try {
            ToolCallbackProvider provider = toolCallbackProvider.getIfAvailable();
            if (provider != null) {
                ToolCallback[] mcpTools = provider.getToolCallbacks();
                return ToolCallbacks.from(localTools, mcpTools);
            }
        } catch (Exception e) {
            // MCP 服务不可用时，降级为仅本地工具
        }
        return localTools;
    }
}
