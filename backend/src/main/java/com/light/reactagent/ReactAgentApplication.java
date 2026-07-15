package com.light.reactagent;

import com.light.reactagent.config.MultiModelProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties(MultiModelProperties.class)
public class ReactAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReactAgentApplication.class, args);
    }

}