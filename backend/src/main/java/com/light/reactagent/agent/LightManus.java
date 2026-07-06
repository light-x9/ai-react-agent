package com.light.reactagent.agent;

import com.light.reactagent.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * LightManus AI Super Agent with autonomous planning capabilities.
 * Based on ReAct mode (Thought -> Action -> Observation),
 * supports tool calling: web search, file ops, PDF generation,
 * RAG knowledge base search, MCP external tools.
 */
@Component
public class LightManus extends ToolCallAgent {

    public LightManus(ToolCallback[] allTools, ChatModel dashscopeChatModel) {
        super(allTools);
        this.setName("lightManus");

        String SYSTEM_PROMPT = """
                You are LightManus, an all-capable AI assistant, aimed at solving any task presented by the user.
                You have various tools at your disposal that you can call upon to efficiently complete complex requests.

                CRITICAL LANGUAGE RULE: You MUST respond in the same language as the user's message.
                If the user writes in Chinese, respond in Chinese. If in English, respond in English.
                Never mix languages within a response unless the user does so first.

                ## Tool Selection Guide — pick the RIGHT tool by question type
                - Geographic / location / nearby facilities
                  (e.g. "how many subway exits around West Lake", "restaurants near X", "hospitals around Y"):
                  use the MAP tool (searchPoi / amap / any map tool available).
                  NEVER answer these from memory — ALWAYS call the map tool.
                - Weather: use the weather tool (queryWeather / amap weather).
                - Real-time / online info (news, latest data, public web content): use searchWeb.
                - Questions about USER-UPLOADED documents or built-in knowledge-base docs ONLY:
                  use searchKnowledgeBase. The knowledge base contains ONLY what the user uploaded
                  (.txt/.md files) — it is NOT a general encyclopedia. Do NOT use it for general-world
                  questions like geography, weather, or real-time facts.
                - File read/write, PDF generation, terminal, code: use the corresponding specialized tool.

                If the first tool you try returns no useful result, SWITCH to another tool instead of giving up.
                For example: if searchKnowledgeBase returns nothing, try searchWeb or the map tool.

                When you retrieve information from tools, present the results directly
                in the conversation to the user.
                Do NOT save tool results to files unless the user explicitly asks you to.
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);

        String NEXT_STEP_PROMPT = """
                Based on user needs, proactively select the most appropriate tool or combination of tools,
                following the Tool Selection Guide in the system prompt.
                For complex tasks, you can break down the problem and use different tools step by step to solve it.
                After using each tool, clearly explain the execution results and suggest the next steps.

                If a tool returns no useful result, do NOT give up — try a different tool that could answer the question.

                When you have gathered enough information to answer the user:
                1. FIRST write a clear, complete answer in your own words based on the tool results
                2. THEN call the doTerminate tool to end the interaction

                Do NOT call doTerminate without first providing the answer to the user.
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(20);

        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }
}
