package com.light.reactagent.tools;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 集中的工具注册类
 * <p>
 * allTools()/allToolsWithMcp() 提供全量工具（兼容旧路径）。
 * buildToolSet(webSearch, knowledgeBase) 按能力开关动态装配工具子集，供能力开关模式使用。
 */
@Configuration
public class ToolRegistration {

    @Value("${lightmanus.tool.terminal.enabled:true}")
    private boolean terminalEnabled;

    private final ObjectProvider<ToolCallbackProvider> toolCallbackProvider;
    private final RagSearchTool ragSearchTool;
    private final ImageSearchTool imageSearchTool;
    private final AmapTool amapTool;
    private final WebSearchTool webSearchTool;

    public ToolRegistration(ObjectProvider<ToolCallbackProvider> toolCallbackProvider,
                            RagSearchTool ragSearchTool,
                            ImageSearchTool imageSearchTool,
                            AmapTool amapTool,
                            WebSearchTool webSearchTool) {
        this.toolCallbackProvider = toolCallbackProvider;
        this.ragSearchTool = ragSearchTool;
        this.imageSearchTool = imageSearchTool;
        this.amapTool = amapTool;
        this.webSearchTool = webSearchTool;
    }

    /**
     * 按能力开关动态装配工具子集
     * - 常驻 TerminateTool（控制 ReAct 流程终止）
     * - 开网页搜索：WebSearchTool + WebScrapingTool
     * - 开知识库：RagSearchTool
     * - 纯对话（都不开）：仅 TerminateTool，无外部工具
     */
    public ToolCallback[] buildToolSet(boolean webSearch, boolean knowledgeBase) {
        List<Object> tools = new ArrayList<>();
        tools.add(new TerminateTool());
        if (webSearch) {
            tools.add(webSearchTool);
            tools.add(new WebScrapingTool());
        }
        if (knowledgeBase) {
            tools.add(ragSearchTool);
        }
        return ToolCallbacks.from(tools.toArray());
    }

    /**
     * 全量本地工具集（兼容旧路径）
     */
    @Bean
    public ToolCallback[] allTools() {
        FileOperationTool fileOperationTool = new FileOperationTool();
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        ResourceDownloadTool resourceDownloadTool = new ResourceDownloadTool();
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
                ragSearchTool,
                imageSearchTool,
                amapTool
        );
    }

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
            // MCP 不可用，降级为仅本地工具
        }
        return localTools;
    }
}
