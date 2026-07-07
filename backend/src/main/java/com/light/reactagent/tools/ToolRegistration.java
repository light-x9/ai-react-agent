package com.light.reactagent.tools;

import com.light.reactagent.tools.file.FileMetadataManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * 集中的工具注册类
 * <p>
 * allTools()/allToolsWithMcp() 提供全量工具（兼容旧路径）。
 * buildToolSet(webSearch, knowledgeBase) 按能力开关动态装配工具子集，供能力开关模式使用。
 */
@Slf4j
@Configuration
public class ToolRegistration {

    @Value("${lightmanus.tool.terminal.enabled:true}")
    private boolean terminalEnabled;

    private final ObjectProvider<ToolCallbackProvider> toolCallbackProvider;
    private final RagSearchTool ragSearchTool;
    private final WebSearchTool webSearchTool;
    private final FileMetadataManager fileMetadataManager;

    public ToolRegistration(ObjectProvider<ToolCallbackProvider> toolCallbackProvider,
                            RagSearchTool ragSearchTool,
                            WebSearchTool webSearchTool,
                            FileMetadataManager fileMetadataManager) {
        this.toolCallbackProvider = toolCallbackProvider;
        this.ragSearchTool = ragSearchTool;
        this.webSearchTool = webSearchTool;
        this.fileMetadataManager = fileMetadataManager;
    }

    /**
     * 按能力开关动态装配工具子集，并拼接 MCP 工具（高德地图 + 图片搜索）
     * - 常驻：TerminateTool + 文件操作工具 + PDF 生成工具 + 资源下载工具
     * - 开网页搜索：WebSearchTool + WebScrapingTool
     * - 开知识库：RagSearchTool
     * - MCP 工具启动时全量注入
     */
    public ToolCallback[] buildToolSet(boolean webSearch, boolean knowledgeBase) {
        List<Object> tools = new ArrayList<>();
        tools.add(new TerminateTool());
        // 文件工具常驻：AI 生成文件/PDF 是核心能力，不依赖能力开关
        tools.add(new FileOperationTool(fileMetadataManager));
        tools.add(new PDFGenerationTool(fileMetadataManager));
        tools.add(new ResourceDownloadTool(fileMetadataManager));
        if (webSearch) {
            tools.add(webSearchTool);
            tools.add(new WebScrapingTool());
        }
        if (knowledgeBase) {
            tools.add(ragSearchTool);
        }
        ToolCallback[] localTools = ToolCallbacks.from(tools.toArray());
        // 拼接 MCP 工具（高德地图 + 图片搜索），MCP 不可用时降级为仅本地工具
        return appendMcpTools(localTools);
    }

    /**
     * 全量本地工具集（兼容旧路径，不含 MCP）
     */
    @Bean
    public ToolCallback[] allTools() {
        FileOperationTool fileOperationTool = new FileOperationTool(fileMetadataManager);
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        ResourceDownloadTool resourceDownloadTool = new ResourceDownloadTool(fileMetadataManager);
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool(terminalEnabled);
        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool(fileMetadataManager);
        TerminateTool terminateTool = new TerminateTool();
        return ToolCallbacks.from(
                fileOperationTool,
                webSearchTool,
                webScrapingTool,
                resourceDownloadTool,
                terminalOperationTool,
                pdfGenerationTool,
                terminateTool,
                ragSearchTool
        );
    }

    /**
     * 全量本地工具 + MCP 工具（兼容旧路径）
     */
    @Bean
    public ToolCallback[] allToolsWithMcp() {
        return appendMcpTools(allTools());
    }

    /**
     * 将 MCP 工具拼接到本地工具数组后面；MCP 不可用时降级返回原始本地工具
     */
    private ToolCallback[] appendMcpTools(ToolCallback[] localTools) {
        try {
            ToolCallbackProvider provider = toolCallbackProvider.getIfAvailable();
            if (provider != null) {
                ToolCallback[] mcpTools = provider.getToolCallbacks();
                // 诊断日志：打印 MCP 工具加载情况
                log.debug("[MCP-DIAG] provider={}, mcpTools.length={}", provider.getClass().getSimpleName(), mcpTools.length);
                for (ToolCallback tc : mcpTools) {
                    log.debug("[MCP-DIAG]   tool={}", tc.getToolDefinition().name());
                }
                if (mcpTools.length > 0) {
                    // 直接拼接两个数组，不使用 ToolCallbacks.from()（它会把 ToolCallback[] 当作待解析对象）
                    ToolCallback[] merged = Stream.concat(Arrays.stream(localTools), Arrays.stream(mcpTools))
                            .toArray(ToolCallback[]::new);
                    log.debug("[MCP-DIAG] merged total={}", merged.length);
                    return merged;
                } else {
                    log.warn("[MCP-DIAG] provider available but 0 MCP tools");
                }
            } else {
                log.warn("[MCP-DIAG] toolCallbackProvider.getIfAvailable() returned null");
            }
        } catch (Exception e) {
            log.warn("[MCP-DIAG] MCP tool loading failed: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        }
        return localTools;
    }
}
