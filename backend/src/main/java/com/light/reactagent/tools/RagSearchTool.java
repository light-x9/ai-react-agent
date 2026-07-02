package com.light.reactagent.tools;

import com.light.reactagent.rag.QueryRewriter;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG knowledge base search tool.
 * Agent can autonomously call this tool in the ReAct reasoning loop
 * to retrieve relevant knowledge from PGvector vector store
 * (includes both preset documents and user-uploaded documents).
 * <p>
 * Flow: user query -> QueryRewriter -> PGVector search -> return TopK results
 */
@Component
public class RagSearchTool {

    /** PGvector vector store, loaded with preset docs at startup */
    private final VectorStore vectorStore;

    /** Query rewriter: converts colloquial input to search-friendly expression */
    private final QueryRewriter queryRewriter;

    public RagSearchTool(
            @Qualifier("pgVectorVectorStore") VectorStore vectorStore,
            QueryRewriter queryRewriter) {
        this.vectorStore = vectorStore;
        this.queryRewriter = queryRewriter;
    }

    /**
     * Search the knowledge base.
     * 1. Rewrite user query for better retrieval
     * 2. Search PGvector store for similar documents
     * 3. Return formatted results
     *
     * @param query user's original question
     * @return search results or fallback message
     */
    @Tool(description = "Search knowledge base for relevant info. Use when user asks about topics that may be covered in uploaded documents or built-in knowledge.")
    public String searchKnowledgeBase(
            @ToolParam(description = "User query to search in knowledge base")
            String query) {

        // Step 1: Query rewrite
        String rewrittenQuery = queryRewriter.doQueryRewrite(query);

        // Step 2: Vector similarity search
        SearchRequest searchRequest = SearchRequest.builder()
                .query(rewrittenQuery)
                .topK(3)
                .build();

        List<Document> similarDocuments = vectorStore.similaritySearch(searchRequest);

        // Step 3: Format results
        if (similarDocuments.isEmpty()) {
            return "No relevant info found in knowledge base. You MUST now answer the user directly using your own knowledge. Do NOT terminate - provide a helpful response.";
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
}