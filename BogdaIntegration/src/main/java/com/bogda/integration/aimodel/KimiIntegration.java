package com.bogda.integration.aimodel;

import com.bogda.common.reporter.ExceptionReporterHolder;
import com.bogda.common.reporter.TraceReporterHolder;
import com.bogda.common.utils.JsonUtils;
import com.bogda.common.utils.TimeOutUtils;
import com.bogda.integration.feishu.FeiShuRobotIntegration;
import com.fasterxml.jackson.databind.JsonNode;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.bogda.common.utils.TimeOutUtils.*;

@Component
public class KimiIntegration {

    @Value("${kimi.key.vault}")
    private String kimiKeyVault;
    @Value("${kimi.id.vault}")
    private String kimiIdVault;
    public static final String KIMI_K25 = "kimi-k2.5";
    private static final String API_URL = "https://api.moonshot.cn/v1/chat/completions";
    public static final int KIMI_COEFFICIENT = 2;

    private HttpClient httpClient;

    @Autowired
    private FeiShuRobotIntegration feiShuRobotIntegration;

    @PostConstruct
    public void init() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        TraceReporterHolder.report("KimiIntegration.init", "KimiIntegration init success "
                + kimiKeyVault + " " + kimiIdVault);
    }

    public Pair<String, Integer> chat(String prompt, String target) {
        return chatWithKimi(KIMI_K25, prompt, target, KIMI_COEFFICIENT);
    }

    public Pair<String, Integer> chatWithKimi(String model, String prompt, String target, double magnification) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "temperature", 0.6,
                    "thinking", Map.of("type", "disabled")
            );
            String json = JsonUtils.OBJECT_MAPPER.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + kimiKeyVault)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofMinutes(5))
                    .build();

            HttpResponse<String> response = TimeOutUtils.callWithTimeoutAndRetry(
                    () -> {
                        try {
                            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                        } catch (Exception e) {
                            TraceReporterHolder.report("KimiIntegration.chat", "FatalException KimiIntegration call error: " + e.getMessage()
                                    + " prompt: " + prompt);
                            ExceptionReporterHolder.report("KimiIntegration.chat", e);
                            feiShuRobotIntegration.sendMessage("FatalException KimiIntegration call error: " + e.getMessage() + " prompt: " + prompt);
                            return null;
                        }
                    },
                    DEFAULT_TIMEOUT, DEFAULT_UNIT,
                    DEFAULT_MAX_RETRIES
            );

            if (response == null || response.statusCode() != 200) {
                String errorBody = (response != null) ? response.body() : "null response";
                TraceReporterHolder.report("KimiIntegration.chat", "FatalException KimiIntegration HTTP error, status: "
                        + (response != null ? response.statusCode() : "null") + " body: " + errorBody
                        + " prompt: " + prompt);
                feiShuRobotIntegration.sendMessage("FatalException KimiIntegration call error prompt: " + prompt);
                return null;
            }

            JsonNode root = JsonUtils.OBJECT_MAPPER.readTree(response.body());
            String content = root.at("/choices/0/message/content").asText();
            int inputTokens = root.at("/usage/prompt_tokens").asInt(0);
            int outputTokens = root.at("/usage/completion_tokens").asInt(0);
            int totalTokens = root.at("/usage/total_tokens").asInt(0);
            int allToken = (int) Math.ceil(totalTokens * magnification);

            TraceReporterHolder.report("KimiIntegration.chat", "KimiIntegration model : " + model + " 提示词 ：" + prompt
                    + " 翻译成：" + content + " all: " + allToken
                    + " input: " + inputTokens + " output: " + outputTokens + " target: " + target);

            return new Pair<>(content, allToken);
        } catch (Exception e) {
            TraceReporterHolder.report("KimiIntegration.chat", "FatalException KimiIntegration chat error: " + e.getMessage()
                    + " prompt: " + prompt);
            ExceptionReporterHolder.report("KimiIntegration.chat", e);
            feiShuRobotIntegration.sendMessage("FatalException KimiIntegration call error: " + e.getMessage() + " prompt: " + prompt);
            return null;
        }
    }
}
