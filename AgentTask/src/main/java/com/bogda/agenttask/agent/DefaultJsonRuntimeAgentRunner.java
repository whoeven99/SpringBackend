package com.bogda.agenttask.agent;

import com.bogda.common.agent.JsonRuntimeAgentRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 封装 {@link JsonRuntimeAgent#chat}，实现 {@link JsonRuntimeAgentRunner}。
 */
@Service
public class DefaultJsonRuntimeAgentRunner implements JsonRuntimeAgentRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultJsonRuntimeAgentRunner.class);

    @Autowired
    private JsonRuntimeAgent jsonRuntimeAgent;

    @Override
    public String run(String message) {
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing parameters: message");
        }
        String trimmed = message.trim();
        LOG.info("json-runtime-agent run start, message={}", shorten(trimmed, 300));
        try {
            String answer = jsonRuntimeAgent.chat(trimmed);
            LOG.info("json-runtime-agent run finish, answer={}", shorten(answer, 500));
            return answer;
        } catch (RuntimeException e) {
            LOG.error("json-runtime-agent run failed, message={}", shorten(trimmed, 300), e);
            throw e;
        }
    }

    private static String shorten(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...(truncated)";
    }
}
