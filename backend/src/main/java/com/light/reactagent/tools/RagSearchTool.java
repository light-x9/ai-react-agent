package com.light.reactagent.tools;

import com.light.reactagent.service.KnowledgeRetrievalService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * RAG 知识库检索工具（多租户隔离）
 * <p>
 * 检索逻辑已统一收口到 {@link KnowledgeRetrievalService}，本类作为工具的薄封装，
 * 供 LLM 在需要时主动发起补充检索（如预检索结果不足）。
 * userId 过滤由 Service 内部通过 SecurityContext 完成。
 */
@Component
public class RagSearchTool {

    private final KnowledgeRetrievalService knowledgeRetrievalService;

    public RagSearchTool(KnowledgeRetrievalService knowledgeRetrievalService) {
        this.knowledgeRetrievalService = knowledgeRetrievalService;
    }

    @Tool(description = "Search the user-uploaded knowledge base documents ONLY. Use ONLY when the question is about content the user uploaded (.txt/.md files) or built-in docs. NOT for general-world questions like geography, weather, or real-time info — use searchWeb or the map tool for those instead.")
    public String searchKnowledgeBase(
            @ToolParam(description = "User query to search in knowledge base")
            String query) {

        String result = knowledgeRetrievalService.retrieve(query);
        if (result == null) {
            return "No relevant info found in the knowledge base. The knowledge base only contains user-uploaded documents. For this question, try a different tool: use the map tool (searchPoi / amap) for geographic or location questions, or searchWeb for general or real-time info. Do NOT give up — switch tools.";
        }

        return "Retrieved from knowledge base:\n\n" + result
                + "\n\nPlease answer based on the above content.";
    }
}
