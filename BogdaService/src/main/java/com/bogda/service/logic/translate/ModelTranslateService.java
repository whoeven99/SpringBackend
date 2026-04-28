package com.bogda.service.logic.translate;

import com.bogda.common.reporter.ExceptionReporterHolder;
import com.bogda.common.reporter.TraceReporterHolder;
import com.bogda.integration.aimodel.KimiIntegration;
import com.bogda.repository.RedisIntegration;
import com.bogda.service.integration.ALiYunTranslateIntegration;
import com.bogda.integration.aimodel.ChatGptIntegration;
import com.bogda.integration.aimodel.GeminiIntegration;
import com.bogda.integration.aimodel.GoogleMachineIntegration;
import com.bogda.common.utils.JsonUtils;
import com.bogda.common.utils.RedisKeyUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ModelTranslateService {
    @Autowired
    private ALiYunTranslateIntegration aLiYunTranslateIntegration;
    @Autowired
    private GeminiIntegration geminiIntegration;
    @Autowired
    private ChatGptIntegration chatGptIntegration;
    @Autowired
    private GoogleMachineIntegration googleMachineIntegration;
    @Autowired
    private AiModelConfigService aiModelConfigService;
    @Autowired
    private KimiIntegration kimiIntegration;
    @Autowired
    private RedisIntegration redisIntegration;

    private static final String SESSION_KEY_PREFIX = "translate_model_session:";
    private static final int MAX_SESSION_MESSAGES = 12;
    private static final int MAX_SESSION_CONTENT_CHARS = 24000;

    // ai translate
    public Pair<String, Integer> aiTranslate(String aiModel, String prompt, String target) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(newMessage("user", prompt));
        return aiTranslateWithMessages(aiModel, messages, target, null);
    }

    public Pair<String, Integer> aiTranslateWithMessages(String aiModel, List<Map<String, String>> messages,
                                                         String target, String sessionId) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        Pair<String, Integer> pair = null;
        String lowerModel = aiModel.toLowerCase();
        String lastPrompt = messages.get(messages.size() - 1).getOrDefault("content", "");
        if (lastPrompt.contains("Apply approved translations when context matches:") || aiModel.equals(ChatGptIntegration.GPT_4_1)) {
            pair = chatGptIntegration.chatWithGpt(ChatGptIntegration.GPT_4_1, ChatGptIntegration.GPT_4_OPENAI_MAGNIFICATION
                    , messages, target, sessionId);
        } else if (lowerModel.contains("qwen")) {
            pair = aLiYunTranslateIntegration.userTranslate(messages, target, aiModelConfigService.getMagnification("qwen"), sessionId);
        } else if (lowerModel.contains("gpt") || "gpt-5-mini".equals(aiModel)) {
            pair = chatGptIntegration.chatWithGpt(
                    aiModelConfigService.getModel("gpt"),
                    aiModelConfigService.getMagnification("gpt"),
                    messages, target, sessionId);
        } else if (lowerModel.contains("gemini")) {
            pair = geminiIntegration.generateText(aiModel, mergeMessagesForGemini(messages), aiModelConfigService.getMagnification("gemini"));
        } else if (lowerModel.contains("kimi")) {
            pair = kimiIntegration.chatWithKimi(aiModel, messages, target, aiModelConfigService.getMagnification("kimi"), sessionId);
        }
        return pair;
    }

    public Pair<String, Integer> modelTranslate(String aiModel, String prompt, String target, String sourceText) {
        return modelTranslate(aiModel, prompt, target, sourceText, null);
    }

    public Pair<String, Integer> modelTranslate(String aiModel, String prompt, String target, String sourceText, String sessionId) {
        Pair<String, Integer> pair = aiTranslateWithSession(aiModel, prompt, target, sourceText, sessionId);

        if (pair != null) {
            return pair;
        }

        // 做一个保底处理，当pair为null的时候，用google再翻译一次，如果再为null，就直接返回.
        TraceReporterHolder.report("ModelTranslateService.modelTranslate", "FatalException  " + aiModel + " 翻译失败， 数据如下，用google翻译 : " + sourceText);
        return googleMachineIntegration.googleTranslateWithSDK(sourceText, target);
    }

    public Pair<String, Integer> modelTranslate(String aiModel, String prompt, String target, Map<Integer, String> sourceMap) {
        return modelTranslate(aiModel, prompt, target, sourceMap, null);
    }

    public Pair<String, Integer> modelTranslate(String aiModel, String prompt, String target,
                                                Map<Integer, String> sourceMap, String sessionId) {
        Pair<String, Integer> pair = aiTranslateWithSession(aiModel, prompt, target, sourceMap, sessionId);

        if (pair != null) {
            return pair;
        }

        // json批量翻译不行，翻译值会少数据，目前只能循环批量翻译
        TraceReporterHolder.report("ModelTranslateService.modelTranslate", "FatalException  " + aiModel + " 翻译失败， 数据如下，用google翻译 : " + sourceMap);

        // 将文本转为Map<Integer, String>, 循环翻译
        if (sourceMap == null || sourceMap.isEmpty()) {
            return null;
        }

        Map<Integer, String> resultMap = new LinkedHashMap<>();
        int totalCount = 0;

        for (Map.Entry<Integer, String> entry : sourceMap.entrySet()) {
            Integer key = entry.getKey();
            String value = entry.getValue();

            if (value == null || value.isBlank()) {
                resultMap.put(key, value);
                continue;
            }

            try {
                Pair<String, Integer> translated = googleMachineIntegration.googleTranslateWithSDK(value, target);

                if (translated != null) {
                    resultMap.put(key, translated.getFirst());
                    totalCount += translated.getSecond();
                } else {
                    resultMap.put(key, value);
                }
            } catch (Exception e) {
                ExceptionReporterHolder.report("ModelTranslateService.modelTranslate", e);
                TraceReporterHolder.report("ModelTranslateService.modelTranslate", "FatalException google机器翻译失败 ：" + value + " key: " + key);
                resultMap.put(key, value);
            }
        }

        return new Pair<String, Integer>(JsonUtils.objectToJson(resultMap), totalCount);

    }

    private Pair<String, Integer> aiTranslateWithSession(String aiModel, String prompt, String target,
                                                         String sourceText, String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return aiTranslate(aiModel, prompt, target);
        }
        SessionState sessionState = getSessionState(sessionId);
        String incrementalContent = buildIncrementalContent(sourceText);
        return doTranslateWithSession(aiModel, target, sessionId, sessionState, prompt, incrementalContent);
    }

    private Pair<String, Integer> aiTranslateWithSession(String aiModel, String prompt, String target,
                                                         Map<Integer, String> sourceMap, String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return aiTranslate(aiModel, prompt, target);
        }
        SessionState sessionState = getSessionState(sessionId);
        String incrementalContent = buildIncrementalContent(sourceMap);
        return doTranslateWithSession(aiModel, target, sessionId, sessionState, prompt, incrementalContent);
    }

    private Pair<String, Integer> doTranslateWithSession(String aiModel, String target, String sessionId,
                                                         SessionState sessionState, String fullPrompt, String incrementalContent) {
        if (shouldRollSession(sessionState, incrementalContent)) {
            sessionState.initialized = false;
            sessionState.messages = new ArrayList<>();
            sessionState.rollCount++;
            TraceReporterHolder.report("ModelTranslateService.doTranslateWithSession",
                    "Session window rolled, sessionId=" + sessionId + " rollCount=" + sessionState.rollCount);
        }

        String userContent = sessionState.initialized ? incrementalContent : fullPrompt;
        List<Map<String, String>> messages = new ArrayList<>(sessionState.messages);
        messages.add(newMessage("user", userContent));

        Pair<String, Integer> pair = aiTranslateWithMessages(aiModel, messages, target, sessionId);
        if (pair == null) {
            return null;
        }

        sessionState.initialized = true;
        sessionState.messages = trimMessages(messages, MAX_SESSION_MESSAGES - 1);
        sessionState.messages.add(newMessage("assistant", pair.getFirst()));
        sessionState.messages = trimMessages(sessionState.messages, MAX_SESSION_MESSAGES);
        saveSessionState(sessionId, sessionState);
        return pair;
    }

    private boolean shouldRollSession(SessionState sessionState, String nextContent) {
        if (sessionState == null || !sessionState.initialized) {
            return false;
        }
        if (sessionState.messages != null && sessionState.messages.size() >= MAX_SESSION_MESSAGES) {
            return true;
        }
        int totalChars = calculateMessagesChars(sessionState.messages) + (nextContent == null ? 0 : nextContent.length());
        return totalChars > MAX_SESSION_CONTENT_CHARS;
    }

    private static int calculateMessagesChars(List<Map<String, String>> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (Map<String, String> message : messages) {
            if (message == null) {
                continue;
            }
            String content = message.get("content");
            if (content != null) {
                total += content.length();
            }
        }
        return total;
    }

    private SessionState getSessionState(String sessionId) {
        String raw = redisIntegration.get(SESSION_KEY_PREFIX + sessionId);
        if (raw == null || raw.isEmpty() || "null".equals(raw)) {
            return new SessionState();
        }
        SessionState state = JsonUtils.jsonToObject(raw, new TypeReference<SessionState>() {
        });
        return state == null ? new SessionState() : state;
    }

    private void saveSessionState(String sessionId, SessionState state) {
        if (state == null) {
            return;
        }
        redisIntegration.set(SESSION_KEY_PREFIX + sessionId, JsonUtils.objectToJson(state), RedisKeyUtils.DAY_14);
    }

    private static List<Map<String, String>> trimMessages(List<Map<String, String>> messages, int max) {
        if (messages == null || messages.size() <= max) {
            return messages == null ? new ArrayList<>() : messages;
        }
        if (messages.isEmpty()) {
            return new ArrayList<>();
        }
        List<Map<String, String>> result = new ArrayList<>();
        Map<String, String> first = messages.get(0);
        result.add(first);
        int remain = max - 1;
        int start = Math.max(1, messages.size() - remain);
        for (int i = start; i < messages.size(); i++) {
            result.add(messages.get(i));
        }
        return result;
    }

    private static String buildIncrementalContent(String sourceText) {
        return "Continue with the same translation rules from this session. Translate only the incremental content below and return only translation text.\n"
                + sourceText;
    }

    private static String buildIncrementalContent(Map<Integer, String> sourceMap) {
        return "Continue with the same translation rules from this session. Translate only the incremental entries below and return strict JSON with the same keys.\n"
                + JsonUtils.objectToJson(sourceMap);
    }

    private static String mergeMessagesForGemini(List<Map<String, String>> messages) {
        StringBuilder builder = new StringBuilder();
        for (Map<String, String> message : messages) {
            if (message == null) {
                continue;
            }
            String role = message.getOrDefault("role", "user");
            String content = message.getOrDefault("content", "");
            builder.append(role).append(": ").append(content).append("\n");
        }
        return builder.toString();
    }

    private static Map<String, String> newMessage(String role, String content) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("role", role);
        map.put("content", content == null ? "" : content);
        return map;
    }

    private static class SessionState {
        public boolean initialized = false;
        public int rollCount = 0;
        public List<Map<String, String>> messages = new ArrayList<>();
    }
}
