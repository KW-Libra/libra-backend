package com.libra.api;

import com.libra.api.auth.AuthProperties;
import com.libra.api.integration.agent.AgentProperties;
import com.libra.api.integration.kis.KisProperties;
import com.libra.api.knowledge.KnowledgeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({KisProperties.class, AgentProperties.class, KnowledgeProperties.class, AuthProperties.class})
public class LibraApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(LibraApiApplication.class, args);
    }
}
