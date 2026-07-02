package com.light.reactagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.ai.mcp.client.autoconfigure.McpClientAutoConfiguration;
import org.springframework.ai.mcp.client.autoconfigure.McpToolCallbackAutoConfiguration;

@SpringBootApplication(exclude = {
    McpClientAutoConfiguration.class,
    McpToolCallbackAutoConfiguration.class
})
public class ReactAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReactAgentApplication.class, args);
    }

}