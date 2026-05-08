package com.bogda.service.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 封装 {@link JsonRuntimeAgent#chat}，供 BogdaApi（HTTP）与 BogdaTask（定时/消息等）复用。
 * 自然语言示例：「执行翻译任务 taskId=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx」
 */
@Service
public class JsonRuntimeAgentRunner {
    private static final Logger LOG = LoggerFactory.getLogger(JsonRuntimeAgentRunner.class);

    @Autowired
    private JsonRuntimeAgent jsonRuntimeAgent;

    /**
     * @param message 自然语言或带 taskId / 完整 JSON 的文本（与 Api /agent/json-runtime/run 一致）
     * @return finalizeTrace 产出的 JSON 字符串
     */
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
