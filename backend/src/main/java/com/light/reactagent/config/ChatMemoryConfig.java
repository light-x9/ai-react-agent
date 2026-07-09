package com.light.reactagent.config;

import com.light.reactagent.chatmemory.FileBasedChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 对话记忆配置。
 *
 * 装配带「滑动窗口 + 摘要压缩」的 FileBasedChatMemory，供 AiAgentController 在每次请求时
 * 按 chatId（多租户前缀）拉取结构化历史喂给 Agent；历史管理从前端字符串拼接迁移到后端。
 */
@Configuration
public class ChatMemoryConfig {

    /**
     * 记忆文件目录，可用环境变量 MEMORY_DIR 覆盖。
     * 默认落在用户目录，与文件存储（FILE_SAVE_DIR）类似持久化、不受 tmp 清理影响。
     */
    private static final String DEFAULT_DIR =
            System.getProperty("user.home") + "/.react-agent/memory";

    @Bean
    public FileBasedChatMemory fileBasedChatMemory(
            @Qualifier("openAiChatModel") ChatModel chatModel) {
        String dir = System.getenv().getOrDefault("MEMORY_DIR", DEFAULT_DIR);
        int maxMessages = 24;       // 窗口：保留最近约 12 轮；每次 add 最多 +1，仅超阈值才压缩
        int summarizeCount = 8;     // 每次压缩把最老的 8 条（≈4 轮）合并为一条摘要
        int maxSingleMessageChars = 6000; // 单条消息字符上限（约 1500 token），超长截断
        return new FileBasedChatMemory(dir, chatModel, maxMessages, summarizeCount, maxSingleMessageChars);
    }
}
