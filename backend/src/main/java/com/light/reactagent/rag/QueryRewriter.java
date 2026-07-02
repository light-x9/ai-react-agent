package com.light.reactagent.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.stereotype.Component;

/**
 * 鏌ヨ閲嶅啓鍣?
 */
@Component
public class QueryRewriter {

    private final QueryTransformer queryTransformer;

    public QueryRewriter(ChatModel dashscopeChatModel) {
        ChatClient.Builder builder = ChatClient.builder(dashscopeChatModel);
        // 鍒涘缓鏌ヨ閲嶅啓杞崲鍣?
        queryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(builder)
                .build();
    }

    /**
     * 鎵ц鏌ヨ閲嶅啓
     *
     * @param prompt
     * @return
     */
    public String doQueryRewrite(String prompt) {
        Query query = new Query(prompt);
        // 鎵ц鏌ヨ閲嶅啓
        Query transformedQuery = queryTransformer.transform(query);
        // 杈撳嚭閲嶅啓鍚庣殑鏌ヨ
        return transformedQuery.text();
    }
}
