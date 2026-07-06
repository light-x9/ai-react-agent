package com.light.reactagent.tools;

import com.light.reactagent.rag.QueryRewriter;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 知识库检索工具（多租户隔离）
 * <p>
 * 检索时按当前登录用户的 userId 过滤，用户只能搜到自己上传的文档。
 * userId 从 SecurityContext 获取（由 BaseAgent.runStream 传播到异步线程）。
 */
@Component
public class RagSearchTool {

    private final VectorStore vectorStore;
    private final QueryRewriter queryRewriter;

    public RagSearchTool(
            @Qualifier("pgVectorVectorStore") VectorStore vectorStore,
            QueryRewriter queryRewriter) {
        this.vectorStore = vectorStore;
        this.queryRewriter = queryRewriter;
    }

    @Tool(description = "Search the user-uploaded knowledge base documents ONLY. Use ONLY when the question is about content the user uploaded (.txt/.md files) or built-in docs. NOT for general-world questions like geography, weather, or real-time info — use searchWeb or the map tool for those instead.")
    public String searchKnowledgeBase(
            @ToolParam(description = "User query to search in knowledge base")
            String query) {

        String userId = currentUserId();
        String rewrittenQuery = queryRewriter.doQueryRewrite(query);

        SearchRequest.Builder builder = SearchRequest.builder()
                .query(rewrittenQuery)
                .topK(3);
        // 多租户隔离：只检索当前用户的文档
        if (userId != null) {
            builder.filterExpression("userId == '" + userId + "'");
        }

        SearchRequest searchRequest = builder.build();
        List<Document> similarDocuments = vectorStore.similaritySearch(searchRequest);

        if (similarDocuments.isEmpty()) {
            return "No relevant info found in the knowledge base. The knowledge base only contains user-uploaded documents. For this question, try a different tool: use the map tool (searchPoi / amap) for geographic or location questions, or searchWeb for general or real-time info. Do NOT give up — switch tools.";
        }

        return "Retrieved from knowledge base:\n\n"
                + similarDocuments.stream()
                        .map(doc -> {
                            Object source = doc.getMetadata().getOrDefault("source", "unknown");
                            return "[source: " + source + "] " + doc.getText();
                        })
                        .collect(Collectors.joining("\n\n"))
                + "\n\nPlease answer based on the above content.";
    }

    /**
     * 从安全上下文取当前用户名（知识库租户 key）
     */
    private String currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() != null
                && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return null;
    }
}
