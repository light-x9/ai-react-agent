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

    /**
     * 终端工具开关：默认开启以保持现有能力，生产环境可通过
     * lightmanus.tool.terminal.enabled=false 关闭（推荐生产关闭）
     */
    @Value("${lightmanus.tool.terminal.enabled:true}")
    private boolean terminalEnabled;

    // 使用 ObjectProvider 延迟获取 MCP 工具，避免 MCP 服务不可用时阻塞启动
    private final ObjectProvider<ToolCallbackProvider> toolCallbackProvider;

    private final RagSearchTool ragSearchTool;
    private final ImageSearchTool imageSearchTool;
    private final AmapTool amapTool;

    public ToolRegistration(ObjectProvider<ToolCallbackProvider> toolCallbackProvider,
                            RagSearchTool ragSearchTool,
                            ImageSearchTool imageSearchTool,
                            AmapTool amapTool) {
        this.toolCallbackProvider = toolCallbackProvider;
        this.ragSearchTool = ragSearchTool;
        this.imageSearchTool = imageSearchTool;
        this.amapTool = amapTool;
    }

    /**
     * 本地工具集：包含文件操作、网页搜索、PDF生成、RAG检索、图片搜索、高德地图等
     */
    @Bean
    public ToolCallback[] allTools() {
        FileOperationTool fileOperationTool = new FileOperationTool();
        WebSearchTool webSearchTool = new WebSearchTool(searchApiKey);
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        ResourceDownloadTool resourceDownloadTool = new ResourceDownloadTool();
        // 终端工具：按配置决定是否启用安全加固版（黑名单 + 沙箱 + 超时）
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool(terminalEnabled);
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
                ragSearchTool,       // RAG 知识库检索
                imageSearchTool,     // 图片搜索（Pexels）
                amapTool             // 高德地图（天气、地点搜索）
        );
    }

    // 合并本地工具 + MCP 工具，供 SuperAgent（LightManus）使用
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
