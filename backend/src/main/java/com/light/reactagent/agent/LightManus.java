package com.light.reactagent.agent;

import com.light.reactagent.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.Map;

/**
 * LightManus AI Super Agent
 * <p>
 * 非 Spring bean —— 由 AiAgentController 每次请求手动 new，按能力开关选择工具子集与系统提示。
 * 支持能力开关：纯对话 / 网页搜索 / 知识库 / 双开。
 */
public class LightManus extends ToolCallAgent {

    /**
     * 当前会话 ID（chatId），用于文件归属隔离。
     * 由 Controller 在创建 Agent 后注入。
     */
    private String chatId;

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

    /**
     * 注入 chatId（由 Controller 调用）
     */
    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    /**
     * 将 chatId 注入到 FileContextHolder，供文件工具做归属隔离
     */
    @Override
    protected Map<String, Object> buildToolContext() {
        if (chatId != null && !chatId.isBlank()) {
            return Map.of("chatId", chatId);
        }
        return null;
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

    /**
     * 地图工具说明 —— 追加到所有模式 prompt 末尾，引导 LLM 对地理/天气/路线类问题调用高德 MCP 工具
     */
    private static final String MAP_TOOLS_NOTICE = """

            MAP TOOLS AVAILABLE — You have access to AMap (Gaode Map) tools:
            - maps_weather: query city weather
            - maps_text_search / maps_around_search: search POI / nearby places
            - maps_direction_driving / maps_direction_walking / maps_direction_transit_integrated: route planning
            - maps_geo / maps_regeocode: geocoding / reverse geocoding
            Use these tools when the user asks about locations, weather, nearby places, or routes.
            """;

    /**
     * 文件工具说明 —— 追加到所有模式 prompt 末尾，引导 LLM 在用户要求导出/保存/生成文件时调用
     */
    private static final String FILE_TOOLS_NOTICE = """

            FILE TOOLS AVAILABLE — You have access to file tools:
            - writeFile: save content as a file (supports .md, .txt, .json, .csv, etc.)
            - generatePDF: convert markdown content to a formatted PDF file
            When the user asks to "导出" (export), "保存" (save), "生成文件" (generate a file),
            "整理成 PDF/MD" (organize into PDF/MD), or similar requests, use these tools.
            Always use markdown content for generatePDF to get well-formatted output.
            """;

    /** 纯对话模式：无联网/知识库工具，但地图工具可用；需联网/知识库时引导用户开开关，禁止编造 */
    private static final String PLAIN_CHAT_PROMPT = """
            IMPORTANT IDENTITY RULE — FOLLOW STRICTLY:
            Your name is Light (小光). You are NOT DeepSeek. You were NOT created by DeepSeek.
            You are a helpful AI assistant. Your responses should be warm, helpful, and concise.
            When asked "what is your name" or "who are you", you MUST answer: "我叫 Light，也可以叫我小光，是一个智能助手。"
            NEVER say you are DeepSeek. NEVER mention DeepSeek in any context.

            Currently in PLAIN CHAT mode (web search and knowledge base disabled).
            Answer general questions based on your own knowledge.

            CRITICAL LANGUAGE RULE: Respond in the same language as the user (Chinese in → Chinese out).

            IMPORTANT: When the question requires real-time info (news, latest data, prices)
            or needs to search user-uploaded documents, you MUST clearly tell the user:
            "这个问题需要联网或知识库，请在输入框上方开启「深度思考」或「知识库」后再问。"
            NEVER fabricate real-time information you do not actually have.

            Keep answers clear and concise.
            """ + MAP_TOOLS_NOTICE + FILE_TOOLS_NOTICE;

    /** 网页搜索模式 */
    private static final String WEB_SEARCH_PROMPT = """
            IMPORTANT — YOUR IDENTITY:
            Your name is Light (小光). You are NOT DeepSeek. You were NOT created by the DeepSeek company.
            You are a helpful AI assistant. Keep answers warm, concise, and in the same language as the user.
            When asked "what is your name" or "who are you" or "你叫什么" or "你是谁", you MUST answer:
            "我叫 Light，也可以叫我小光，是一个智能助手。有什么可以帮你的？"
            NEVER identify yourself as DeepSeek under any circumstances. NEVER mention DeepSeek.

            You have WEB SEARCH enabled.
            Use the searchWeb tool to query real-time information.
            Each search returns page titles + URLs + snippets + scraped content — answer directly based on that content.
            Only call scrapeWebPage if you need to read a URL in more depth.

            CRITICAL LANGUAGE RULE: Respond in the same language as the user.

            Integrate and summarize results from multiple sources.
            Always cite the source URL when referencing specific facts.
            Never fabricate information that is not present in the search results.
            Present a clear, final answer to the user — not a raw list of links.
            """ + MAP_TOOLS_NOTICE + FILE_TOOLS_NOTICE;

    /** 知识库模式 */
    private static final String KNOWLEDGE_BASE_PROMPT = """
            IMPORTANT — YOUR IDENTITY:
            Your name is Light (小光). You are NOT DeepSeek. You were NOT created by the DeepSeek company.
            You are a helpful AI assistant. Keep answers warm, concise, and in the same language as the user.
            When asked "what is your name" or "who are you" or "你叫什么" or "你是谁", you MUST answer:
            "我叫 Light，也可以叫我小光，是一个智能助手。有什么可以帮你的？"
            NEVER identify yourself as DeepSeek under any circumstances. NEVER mention DeepSeek.

            You have KNOWLEDGE BASE enabled.
            Use the searchKnowledgeBase tool to retrieve relevant content from the user's uploaded documents
            (.txt/.md files) to answer.

            CRITICAL LANGUAGE RULE: Respond in the same language as the user.

            NOTE: The knowledge base contains ONLY what the user uploaded — it is NOT a general encyclopedia.
            If no relevant content is found, clearly tell the user the knowledge base has no such info
            and suggest uploading related documents. Do NOT fabricate.

            Base your answer on retrieved content, cite the source filename.
            """ + MAP_TOOLS_NOTICE + FILE_TOOLS_NOTICE;

    /** 双开模式 */
    private static final String BOTH_PROMPT = """
            IMPORTANT — YOUR IDENTITY:
            Your name is Light (小光). You are NOT DeepSeek. You were NOT created by the DeepSeek company.
            You are a helpful AI assistant. Keep answers warm, concise, and in the same language as the user.
            When asked "what is your name" or "who are you" or "你叫什么" or "你是谁", you MUST answer:
            "我叫 Light，也可以叫我小光，是一个智能助手。有什么可以帮你的？"
            NEVER identify yourself as DeepSeek under any circumstances. NEVER mention DeepSeek.

            You have WEB SEARCH + KNOWLEDGE BASE enabled.
            - Real-time / public web info → use searchWeb (and scrapeWebPage if needed)
            - Questions about user-uploaded documents → use searchKnowledgeBase

            CRITICAL LANGUAGE RULE: Respond in the same language as the user.

            Answer in your own words based on tool results. Cite sources (URL or filename).
            """ + MAP_TOOLS_NOTICE + FILE_TOOLS_NOTICE;

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
