package com.light.reactagent.service;

import com.light.reactagent.rag.QueryRewriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识库预检索服务
 * <p>
 * 供 AiAgentController（预检索式 RAG）和 RagSearchTool（兜底补充检索）共用，
 * 确保检索逻辑只写一处。
 */
@Slf4j
@Service
public class KnowledgeRetrievalService {

    private final VectorStore vectorStore;
    private final QueryRewriter queryRewriter;

    public KnowledgeRetrievalService(
            @Qualifier("pgVectorVectorStore") VectorStore vectorStore,
            QueryRewriter queryRewriter) {
        this.vectorStore = vectorStore;
        this.queryRewriter = queryRewriter;
    }

    /**
     * 查询当前用户知识库，返回格式化后的上下文文本。
     * 检索为空时返回 null，调用方据此判断是否需要拼入 prompt。
     *
     * @param query 用户原始提问
     * @return 形如 "[source: 简历.md] 教育背景：...\n\n[source: ...] ..."，无结果返回 null
     */
    public String retrieve(String query) {
        String userId = currentUserId();
        String rewrittenQuery = queryRewriter.doQueryRewrite(query);

        SearchRequest.Builder builder = SearchRequest.builder()
                .query(rewrittenQuery)
                .topK(3);
        // 多租户隔离：只检索当前用户的文档
        if (userId != null) {
            builder.filterExpression("userId == '" + userId.replace("'", "''") + "'");
        }

        List<Document> similarDocuments = vectorStore.similaritySearch(builder.build());
        if (similarDocuments.isEmpty()) {
            log.debug("知识库预检索：用户 [{}] 查询 [{}] 无命中", userId, query);
            return null;
        }

        String result = similarDocuments.stream()
                .map(doc -> {
                    Object source = doc.getMetadata().getOrDefault("source", "unknown");
                    return "[source: " + source + "] " + doc.getText();
                })
                .collect(java.util.stream.Collectors.joining("\n\n"));

        log.debug("知识库预检索：用户 [{}] 查询 [{}] 命中 {} 条", userId, query, similarDocuments.size());
        return result;
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
