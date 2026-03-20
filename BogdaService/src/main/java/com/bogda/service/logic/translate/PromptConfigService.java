package com.bogda.service.logic.translate;

import com.bogda.common.utils.PromptUtils;
import com.bogda.service.logic.redis.ConfigRedisRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class PromptConfigService {

    private static final String BASE_PROMPT_PREFIX = "MODULE_BASE_PROMPT:";

    @Autowired
    private ConfigRedisRepo configRedisRepo;

    public String getModuleBasePrompt(String module) {
        if (module == null || module.isEmpty()) {
            return null;
        }
        String value = configRedisRepo.getConfig(BASE_PROMPT_PREFIX + module);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        return value;
    }

    public String buildJsonPrompt(String module, String target,
                                  Map<Integer, String> sourceMap,
                                  String termRules, String styleRules) {
        String customBasePrompt = getModuleBasePrompt(module);
        return PromptUtils.buildDynamicJsonPrompt(target, sourceMap, termRules, styleRules, customBasePrompt);
    }

    public String buildSinglePrompt(String module, String targetLanguage,
                                    String text, String termRules, String styleRules) {
        String customBasePrompt = getModuleBasePrompt(module);
        return PromptUtils.buildDynamicSinglePrompt(targetLanguage, text, termRules, styleRules, customBasePrompt);
    }

    public String buildHandlePrompt(String module, String target, String sourceText) {
        String customBasePrompt = getModuleBasePrompt(module);
        return PromptUtils.buildDynamicHandlePrompt(target, sourceText, customBasePrompt);
    }

    public String buildGlossaryJsonPrompt(String module, String target,
                                          String glossaryMapping,
                                          Map<Integer, String> textMap) {
        return buildJsonPrompt(module, target, textMap, glossaryMapping, null);
    }

    public String buildPlainJsonPrompt(String module, String target,
                                       Map<Integer, String> textMap) {
        return buildJsonPrompt(module, target, textMap, null, null);
    }

    public String buildSinglePromptWithFieldRule(String module, String targetLanguage,
                                                String text, String termRules, String styleRules,
                                                String nodeKey) {
        String customBasePrompt = getModuleBasePrompt(module);
        return PromptUtils.buildDynamicSinglePromptWithFieldRule(targetLanguage, text, termRules, styleRules, customBasePrompt, nodeKey);
    }

    public String buildGlossarySinglePrompt(String module, String targetLanguage,
                                            String text, String glossaryMapping) {
        return buildSinglePrompt(module, targetLanguage, text, glossaryMapping, null);
    }

    public String buildPlainSinglePrompt(String module, String targetLanguage, String text) {
        return buildSinglePrompt(module, targetLanguage, text, null, null);
    }
}
