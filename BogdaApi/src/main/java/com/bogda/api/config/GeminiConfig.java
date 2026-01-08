package com.bogda.api.config;

import com.bogda.common.utils.ConfigUtils;
import com.google.genai.Client;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeminiConfig {
    @Bean
    public Client geminiClient() {
        return Client.builder()
                .apiKey(ConfigUtils.getConfig("GEMINI_API_KEY"))
                .build();
    }
}
