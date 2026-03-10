package com.bogda.common.utils;

import com.vdurmont.emoji.EmojiManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class PromptUtils {
    private static final String BASE_PROMPT = """
            You are a professional e-commerce translator.
            Translate the values in {{SOURCE_LANGUAGE_LIST}} into {{TARGET_LANGUAGE}}.
            """;

    private static final String CONTEXT_RULE = """
            Context Rule:
            Use context-aware translation suitable for product, UI, or descriptive content.
            Maintain consistent terminology for identical concepts and avoid misleading literal translations.
            """;

    private static final String TERMINOLOGY_RULE = """
            Terminology:
            Apply approved translations when context matches:
            {{TERM_RULES}}
            """;

    private static final String STYLE_RULE = """
            Follow this translation style:
            {{STYLE_RULES}}
            """;

    private static final String PROTECTION_RULE = """
            Protection:
            Preserve variables, placeholders, HTML tags, URLs, and emojis unchanged((e.g., {{aaa}}, {{aa.bbb}}, {% ccc %}, {% capture email_title %}, [ddd] etc.).
            """;

    private static final String JSON_OUTPUT_RULE = """
            Output:
            Translate values only and return the result in the EXACT same JSON structure.
            """;

    private static final String SINGLE_OUTPUT_RULE = """
            Output:
            Return only the translated text.
            """;

    private static final String HANDLE_OUTPUT_RULE = """
            Output:
            return the text unchanged. Otherwise, proceed as normal. Do not output any notes, annotations, explanations, corrections, or bilingual text. Even if you detect an error in the original, do not mention it—only output the final correct translation. The output should preserve the exact letter casing as the original text — do not capitalize words unless they are capitalized in the source.
            """;

    private static final Pattern VARIABLE_PATTERN = Pattern.compile(
            "\\{\\{\\s*[^{}]*?\\s*\\}\\}"      // {{ ... }}
                    + "|\\{%\\s*.*?\\s*%\\}"   // {% ... %}
                    + "|%\\{\\s*[^{}]*?\\s*\\}"// %{...}
                    + "|<[^>]+>"
                    + "|https?://\\S+"
                    + "|www\\.\\S+"
    );

    public static String buildDynamicJsonPrompt(String target, Map<Integer, String> sourceMap,
                                                String termRules, String styleRules) {
        String sourceLanguageList = JsonUtils.objectToJson(sourceMap);
        String targetLanguage = ModuleCodeUtils.getLanguageName(target);
        boolean includeProtection = hasSpecialContent(sourceMap);
        return buildDynamicPrompt(BASE_PROMPT
                        .replace("{{SOURCE_LANGUAGE_LIST}}", sourceLanguageList)
                        .replace("{{TARGET_LANGUAGE}}", targetLanguage),
                true, termRules, styleRules, includeProtection, JSON_OUTPUT_RULE);
    }

    public static String buildDynamicSinglePrompt(String targetLanguage, String text,
                                                  String termRules, String styleRules) {
        String safeText = text == null ? "" : text;
        boolean includeProtection = hasSpecialContent(safeText);
        String prompt = """
                You are a professional e-commerce translator.
                Translate the text in {{SOURCE_TEXT}} into {{TARGET_LANGUAGE}}.
                """;
        return buildDynamicPrompt(prompt
                        .replace("{{SOURCE_TEXT}}", safeText)
                        .replace("{{TARGET_LANGUAGE}}", ModuleCodeUtils.getLanguageName(targetLanguage)),
                true, termRules, styleRules, includeProtection, SINGLE_OUTPUT_RULE);
    }

    public static String buildDynamicHandlePrompt(String target, String sourceText) {
        Map<Integer, String> sourceMap = new LinkedHashMap<>();
        sourceMap.put(1, sourceText == null ? "" : sourceText);
        String sourceLanguageList = JsonUtils.objectToJson(sourceMap);
        String targetLanguage = ModuleCodeUtils.getLanguageName(target);
        return buildDynamicPrompt(BASE_PROMPT
                        .replace("{{SOURCE_LANGUAGE_LIST}}", sourceLanguageList)
                        .replace("{{TARGET_LANGUAGE}}", targetLanguage),
                false, null, null, false, HANDLE_OUTPUT_RULE);
    }

    /**
     * JSON 批量翻译 — 支持外部 BasePrompt
     * @param customBasePrompt 为 null 时使用默认 BASE_PROMPT
     */
    public static String buildDynamicJsonPrompt(String target, Map<Integer, String> sourceMap,
                                                String termRules, String styleRules,
                                                String customBasePrompt) {
        String sourceLanguageList = JsonUtils.objectToJson(sourceMap);
        String targetLanguage = ModuleCodeUtils.getLanguageName(target);
        boolean includeProtection = hasSpecialContent(sourceMap);
        String base = (customBasePrompt != null) ? customBasePrompt : BASE_PROMPT;
        return buildDynamicPrompt(base
                        .replace("{{SOURCE_LANGUAGE_LIST}}", sourceLanguageList)
                        .replace("{{TARGET_LANGUAGE}}", targetLanguage),
                true, termRules, styleRules, includeProtection, JSON_OUTPUT_RULE);
    }

    /**
     * 单条翻译 — 支持外部 BasePrompt
     * @param customBasePrompt 为 null 时使用默认 BASE_PROMPT
     */
    public static String buildDynamicSinglePrompt(String targetLanguage, String text,
                                                  String termRules, String styleRules,
                                                  String customBasePrompt) {
        String safeText = text == null ? "" : text;
        boolean includeProtection = hasSpecialContent(safeText);
        String base = (customBasePrompt != null) ? customBasePrompt : BASE_PROMPT;
        return buildDynamicPrompt(base
                        .replace("{{SOURCE_TEXT}}", safeText)
                        .replace("{{TARGET_LANGUAGE}}", ModuleCodeUtils.getLanguageName(targetLanguage)),
                true, termRules, styleRules, includeProtection, SINGLE_OUTPUT_RULE);
    }

    /**
     * Handle 翻译 — 支持外部 BasePrompt
     * @param customBasePrompt 为 null 时使用默认 BASE_PROMPT
     */
    public static String buildDynamicHandlePrompt(String target, String sourceText,
                                                  String customBasePrompt) {
        Map<Integer, String> sourceMap = new LinkedHashMap<>();
        sourceMap.put(1, sourceText == null ? "" : sourceText);
        String sourceLanguageList = JsonUtils.objectToJson(sourceMap);
        String targetLanguage = ModuleCodeUtils.getLanguageName(target);
        String base = (customBasePrompt != null) ? customBasePrompt : BASE_PROMPT;
        return buildDynamicPrompt(base
                        .replace("{{SOURCE_LANGUAGE_LIST}}", sourceLanguageList)
                        .replace("{{TARGET_LANGUAGE}}", targetLanguage),
                false, null, null, false, HANDLE_OUTPUT_RULE);
    }

    public static String GlossaryJsonPrompt(String target, String glossaryMapping,
                                            Map<Integer, String> glossaryTextMap) {
        return buildDynamicJsonPrompt(target, glossaryTextMap, glossaryMapping, null);
    }

    public static String JsonPrompt(String target, Map<Integer, String> originalTextMap) {
        return buildDynamicJsonPrompt(target, originalTextMap, null, null);
    }

    public static String GlossarySinglePrompt(String targetLanguage, String text,
                                              String glossaryMapping) {
        return buildDynamicSinglePrompt(targetLanguage, text, glossaryMapping, null);
    }

    public static String SinglePrompt(String targetLanguage, String text) {
        return buildDynamicSinglePrompt(targetLanguage, text, null, null);
    }

    private static String buildDynamicPrompt(String basePrompt,
                                             boolean includeContextRule,
                                             String termRules,
                                             String styleRules,
                                             boolean includeProtectionRule,
                                             String outputRule) {
        StringBuilder prompt = new StringBuilder(basePrompt.trim()).append("\n\n");
        if (includeContextRule) {
            prompt.append(CONTEXT_RULE.trim()).append("\n\n");
        }
        if (termRules != null && !termRules.trim().isEmpty()) {
            prompt.append(TERMINOLOGY_RULE.replace("{{TERM_RULES}}", termRules.trim()).trim()).append("\n\n");
        }
        if (styleRules != null && !styleRules.trim().isEmpty()) {
            prompt.append(STYLE_RULE.replace("{{STYLE_RULES}}", styleRules.trim()).trim()).append("\n\n");
        }
        if (includeProtectionRule) {
            prompt.append(PROTECTION_RULE.trim()).append("\n\n");
        }
        prompt.append(outputRule.trim());
        return prompt.toString();
    }

    private static boolean hasSpecialContent(Map<Integer, String> sourceMap) {
        if (sourceMap == null || sourceMap.isEmpty()) {
            return false;
        }
        return sourceMap.values().stream().anyMatch(PromptUtils::hasSpecialContent);
    }

    private static boolean hasSpecialContent(String source) {
        if (source == null || source.isEmpty()) {
            return false;
        }
        if (VARIABLE_PATTERN.matcher(source).find()) {
            return true;
        }

        return EmojiManager.containsEmoji(source);
    }
}
