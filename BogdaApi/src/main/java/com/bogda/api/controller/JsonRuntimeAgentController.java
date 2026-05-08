package com.bogda.api.controller;

import com.bogda.api.agent.JsonRuntimeAgent;
import com.bogda.common.controller.response.BaseResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent/json-runtime")
public class JsonRuntimeAgentController {
    private static final Logger LOG = LoggerFactory.getLogger(JsonRuntimeAgentController.class);

    @Autowired
    private JsonRuntimeAgent jsonRuntimeAgent;

    @PostMapping("/run")
    public BaseResponse<Object> run(@RequestParam String message) {
        if (message == null || message.trim().isEmpty()) {
            return BaseResponse.FailedResponse("Missing parameters: message");
        }
        try {
            LOG.info("agent-controller /run start, message={}", shorten(message, 300));
            String answer = jsonRuntimeAgent.chat(message);
            LOG.info("agent-controller /run finish, answer={}", shorten(answer, 500));
            return BaseResponse.SuccessResponse(answer);
        } catch (Exception e) {
            LOG.error("agent-controller /run failed, message={}", shorten(message, 300), e);
            return BaseResponse.FailedResponse("Agent run failed: " + e.getMessage());
        }
    }

    private String shorten(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...(truncated)";
    }
}
