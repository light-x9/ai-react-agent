package com.light.reactagent.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 鍩轰簬 AI 鐨勬枃妗ｅ厓淇℃伅澧炲己鍣紙涓烘枃妗ｈˉ鍏呭厓淇℃伅锛?
 */
@Component
public class MyKeywordEnricher {

    @Resource
    private ChatModel openaiChatModel;

    public List<Document> enrichDocuments(List<Document> documents) {
        KeywordMetadataEnricher keywordMetadataEnricher = new KeywordMetadataEnricher(openaiChatModel, 5);
        return  keywordMetadataEnricher.apply(documents);
    }
}
