package com.light.reactagent.agent;

import com.light.reactagent.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

/**
 * LightManus AI Super Agent
 * <p>
 * 非 Spring bean —— 由 AiAgentController 每次请求手动 new，按能力开关选择工具子集与系统提示。
 * 支持能力开关：纯对话 / 网页搜索 / 知识库 / 双开。
 */
public class LightManus extends ToolCallAgent {

    public LightManus(ToolCallback[] tools, ChatModel dashscopeChatModel,
                      boolean webSearch, boolean knowledgeBase) {
        super(tools);
        this.setName("lightManus");

        this.setSystemPrompt(buildSystemPrompt(webSearch, knowledgeBase));
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        // 纯对话模式无工具，限制步数防空转；开工具则允许更多推理步
        this.setMaxSteps(webSearch || knowledgeBase ? 20 : 3);

        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }

    private String buildSystemPrompt(boolean webSearch, boolean knowledgeBase) {
        if (webSearch && knowledgeBase) {
            return BOTH_PROMPT;
        } else if (webSearch) {
            return WEB_SEARCH_PROMPT;
        } else if (knowledgeBase) {
            return KNOWLEDGE_BASE_PROMPT;
        } else {
            return PLAIN_CHAT_PROMPT;
        }
    }

    /** 纯对话模式：无工具，需联网/知识库时引导用户开开关，禁止编造 */
    private static final String PLAIN_CHAT_PROMPT = """
            You are LightManus, an AI assistant. Currently in PLAIN CHAT mode (no tools enabled).
            Answer questions based on your own knowledge.

            CRITICAL LANGUAGE RULE: Respond in the same language as the user (Chinese in → Chinese out).

            IMPORTANT: When the question requires real-time info (news, latest data, weather, prices)
            or needs to search user-uploaded documents, you MUST clearly tell the user:
            "这个问题需要联网/知识库，请在输入框上方开启【网页搜索】或【知识库】开关后再问。"
            NEVER fabricate real-time information you do not actually have.

            Keep answers clear and concise.
            """;

    /** 网页搜索模式 */
    private static final String WEB_SEARCH_PROMPT = """
            You are LightManus, an AI assistant with WEB SEARCH enabled.
            Use the searchWeb tool to query real-time information.
            Each search returns page titles + URLs + snippets + scraped content — answer directly based on that content.
            Only call scrapeWebPage if you need to read a URL in more depth.

            CRITICAL LANGUAGE RULE: Respond in the same language as the user.

            Integrate and summarize results from multiple sources.
            Always cite the source URL when referencing specific facts.
            Never fabricate information that is not present in the search results.
            Present a clear, final answer to the user — not a raw list of links.
            """;

    /** 知识库模式 */
    private static final String KNOWLEDGE_BASE_PROMPT = """
            You are LightManus, an AI assistant with KNOWLEDGE BASE enabled.
            Use the searchKnowledgeBase tool to retrieve relevant content from the user's uploaded documents
            (.txt/.md files) to answer.

            CRITICAL LANGUAGE RULE: Respond in the same language as the user.

            NOTE: The knowledge base contains ONLY what the user uploaded — it is NOT a general encyclopedia.
            If no relevant content is found, clearly tell the user the knowledge base has no such info
            and suggest uploading related documents. Do NOT fabricate.

            Base your answer on retrieved content, cite the source filename.
            """;

    /** 双开模式 */
    private static final String BOTH_PROMPT = """
            You are LightManus, an AI assistant with WEB SEARCH + KNOWLEDGE BASE enabled.
            - Real-time / public web info → use searchWeb (and scrapeWebPage if needed)
            - Questions about user-uploaded documents → use searchKnowledgeBase

            CRITICAL LANGUAGE RULE: Respond in the same language as the user.

            Answer in your own words based on tool results. Cite sources (URL or filename).
            """;

    private static final String NEXT_STEP_PROMPT = """
            Based on user needs, select the appropriate tool to gather information, then answer.
            If a tool returns no useful result, try a different approach.

            When you have gathered enough information to answer the user:
            1. FIRST write a clear, complete answer in your own words based on the tool results
            2. THEN call the doTerminate tool to end the interaction

            In plain chat mode (no tools), just answer directly and call doTerminate.
            Do NOT call doTerminate without first providing the answer to the user.
            """;
}
