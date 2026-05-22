package com.bogda.agenttask.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AgentRootController {

    @Value("${spring.application.name:agent-task}")
    private String applicationName;

    @GetMapping("/")
    public Map<String, String> root() {
        return Map.of(
                "service", applicationName,
                "health", "/actuator/health"
        );
    }
}
