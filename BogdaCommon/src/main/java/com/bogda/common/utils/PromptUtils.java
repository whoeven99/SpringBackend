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
            Translate values only and return the result in the EXACT same JSON structure with unicode.
            """;

    private static final String SINGLE_OUTPUT_RULE = """
            Output:
            Return only the translated text.
            """;

    private static final String HANDLE_OUTPUT_RULE = """
            Output:
            return the text unchanged. Otherwise, proceed as normal. Do not output any notes, annotations, explanations, corrections, or bilingual text. Even if you detect an error in the original, do not mention it—only output the final correct translation. The output should preserve the exact letter casing as the original text — do not capitalize words unless they are capitalized in the source.
            """;

    private static final String UNIT_RULE = """
            Localization:
            Adapt measurements, units, number formatting, and date formats to common conventions in the target language when appropriate.
            Use natural expressions for sizes, dimensions, and e-commerce terminology.
            Do not modify units or numbers if they are part of official product specifications.
            """;

    /**
     * 字段规则 ：Field Rule
     * title/seo_title: concise, keywords
     * description: natural
     * meta_description: marketing
     */
    private static final String FIELD_RULE = """
            [Field Rule]
            Rewrite the text to be under {{FIELD_LENGTH}} characters (including spaces).
            Keep key product terms.
            Remove less important details.
            Keep it natural and fluent.
            """;

    private static final Map<String, Map<String, String>> FIELD_RULES = new LinkedHashMap<>();
    static {
        FIELD_RULES.put("title", Map.of(
                "FIELD_LENGTH", "230",
                "FIELD_RULE", "title: concise, keywords"
        ));
        FIELD_RULES.put("meta_description", Map.of(
                "FIELD_LENGTH", "300",
                "FIELD_RULE", "meta_description: marketing"
        ));
    }

    private static final Pattern VARIABLE_PATTERN = Pattern.compile(
            "\\{\\{\\s*[^{}]*?\\s*\\}\\}"      // {{ ... }}
                    + "|\\{%\\s*.*?\\s*%\\}"   // {% ... %}
                    + "|%\\{\\s*[^{}]*?\\s*\\}"// %{...}
                    + "|<[^>]+>"               // <tag>
                    + "|\\[\\s*[^\\[\\]]+\\s*\\]" // [ ... ]
                    + "|https?://\\S+"
                    + "|www\\.\\S+"
    );


    /**
     * JSON 批量翻译 — 支持外部 BasePrompt
     *
     * @param customBasePrompt 为 null 时使用默认 BASE_PROMPT
     */
    public static String buildDynamicJsonPrompt(String target, Map<Integer, String> sourceMap,
                                                String termRules, String styleRules,
                                                String customBasePrompt) {
        Map<Integer, String> encodedSourceMap = encodeQuotesToUnicodeBeforeJson(sourceMap);
        String sourceLanguageList = JsonUtils.objectToJson(encodedSourceMap);
        String targetLanguage = ModuleCodeUtils.getLanguageName(target);
        boolean includeProtection = hasSpecialContent(sourceMap);
        boolean includeUnit = hasDigit(sourceMap);
        String base = (customBasePrompt != null) ? customBasePrompt : BASE_PROMPT;
        return buildDynamicPrompt(base
                        .replace("{{SOURCE_LANGUAGE_LIST}}", sourceLanguageList)
                        .replace("{{TARGET_LANGUAGE}}", targetLanguage),
                true, termRules, styleRules, includeProtection, includeUnit, null, JSON_OUTPUT_RULE);
    }

    /**
     * 在 JSON 序列化前，将字符串值里的双引号替换为“反斜杠 + u0022”这 6 个字符的字面量。
     * <p>
     * 注意：Java 源码（包括注释）里不能出现连续的“反斜杠 + u”字符序列，否则会触发编译期 unicode 转义。
     */
    private static Map<Integer, String> encodeQuotesToUnicodeBeforeJson(Map<Integer, String> sourceMap) {
        if (sourceMap == null || sourceMap.isEmpty()) {
            return sourceMap;
        }
        String quoteUnicode = "\\" + "u0022"; // 避免在源码里直接出现 反斜杠+uXXXX 触发 Java unicode 转义

        boolean changed = false;
        Map<Integer, String> out = new LinkedHashMap<>(sourceMap.size());
        for (Map.Entry<Integer, String> entry : sourceMap.entrySet()) {
            String v = entry.getValue();
            if (v != null && v.indexOf('"') >= 0) {
                v = v.replace("\"", quoteUnicode);
                changed = true;
            }
            out.put(entry.getKey(), v);
        }
        return changed ? out : sourceMap;
    }

    /**
     * 单条翻译 — 支持外部 BasePrompt
     *
     * @param customBasePrompt 为 null 时使用默认 BASE_PROMPT
     */
    public static String buildDynamicSinglePrompt(String targetLanguage, String text,
                                                  String termRules, String styleRules,
                                                  String customBasePrompt) {
        String safeText = text == null ? "" : text;
        boolean includeProtection = hasSpecialContent(safeText);
        boolean includeUnit = hasDigit(safeText);
        String base = (customBasePrompt != null) ? customBasePrompt : BASE_PROMPT;
        return buildDynamicPrompt(base
                        .replace("{{SOURCE_LANGUAGE_LIST}}", safeText)
                        .replace("{{TARGET_LANGUAGE}}", ModuleCodeUtils.getLanguageName(targetLanguage)),
                true, termRules, styleRules, includeProtection, includeUnit, null, SINGLE_OUTPUT_RULE);
    }

    /**
     * 单条翻译 + FIELD_RULE（用于重试保存失败的任务，根据 nodeKey 增加字段长度约束）
     *
     * @param customBasePrompt 为 null 时使用默认 BASE_PROMPT
     * @param nodeKey          Shopify 节点 key，如 "title"、"meta_description"，用于匹配 FIELD_RULES
     */
    public static String buildDynamicSinglePromptWithFieldRule(String targetLanguage, String text,
                                                               String termRules, String styleRules,
                                                               String customBasePrompt, String nodeKey) {
        String safeText = text == null ? "" : text;
        boolean includeProtection = hasSpecialContent(safeText);
        boolean includeUnit = hasDigit(safeText);
        String base = (customBasePrompt != null) ? customBasePrompt : BASE_PROMPT;
        String fieldRule = resolveFieldRule(nodeKey);
        return buildDynamicPrompt(base
                        .replace("{{SOURCE_LANGUAGE_LIST}}", safeText)
                        .replace("{{TARGET_LANGUAGE}}", ModuleCodeUtils.getLanguageName(targetLanguage)),
                true, termRules, styleRules, includeProtection, includeUnit, fieldRule, SINGLE_OUTPUT_RULE);
    }

    private static String resolveFieldRule(String nodeKey) {
        if (nodeKey == null || nodeKey.isEmpty()) {
            return null;
        }
        Map<String, String> rule = FIELD_RULES.get(nodeKey);
        if (rule == null) {
            return null;
        }
        return FIELD_RULE
                .replace("{{FIELD_RULE}}", rule.get("FIELD_RULE"))
                .replace("{{FIELD_LENGTH}}", rule.get("FIELD_LENGTH"));
    }

    /**
     * Handle 翻译 — 支持外部 BasePrompt
     *
     * @param customBasePrompt 为 null 时使用默认 BASE_PROMPT
     */
    public static String buildDynamicHandlePrompt(String target, String sourceText,
                                                  String customBasePrompt) {
        String targetLanguage = ModuleCodeUtils.getLanguageName(target);
        String base = (customBasePrompt != null) ? customBasePrompt : BASE_PROMPT;
        return buildDynamicPrompt(base
                        .replace("{{SOURCE_LANGUAGE_LIST}}", sourceText)
                        .replace("{{TARGET_LANGUAGE}}", targetLanguage),
                false, null, null, false, hasDigit(sourceText), null, HANDLE_OUTPUT_RULE);
    }

    private static String buildDynamicPrompt(String basePrompt, boolean includeContextRule, String termRules,
                                             String styleRules, boolean includeProtectionRule, boolean includeUnitRule,
                                             String fieldRule, String outputRule) {
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
        if (includeUnitRule) {
            prompt.append(UNIT_RULE.trim()).append("\n\n");
        }
        if (fieldRule != null && !fieldRule.trim().isEmpty()) {
            prompt.append(fieldRule.trim()).append("\n\n");
        }
        prompt.append(outputRule.trim());
        return prompt.toString();
    }

    private static boolean hasDigit(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (Character.isDigit(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDigit(Map<Integer, String> sourceMap) {
        if (sourceMap == null || sourceMap.isEmpty()) {
            return false;
        }
        return sourceMap.values().stream().anyMatch(PromptUtils::hasDigit);
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

    /** 基础单条翻译提示词*/
    private static String BASE_SINGLE_PROMPT = "You are an e-commerce translation expert.";

    /** 原文和上次翻译文本提示词*/
    private static String COMPARE_SINGLE_PROMPT = """
            [Original]
            {{ORIGINAL_TEXT}}

            [Translation]
            {{TRANSLATION_TEXT}}

            Task:
            Improve the translation.
            """;

    /** 要求提示词*/
    private static String REQUIRE_SINGLE_PROMPT = """
            Requirements:
                - Preserve meaning and ensure accuracy
                - Make it natural and fluent in {{TARGET_LANGUAGE}}
            """;

    /** 词汇表提示词*/
    private static String GLOSSAYR_SINGLE_PROMPT = """
                - Keep terminology consistent:{{TERM_RULES}}
            """;

    /** 返回提示词*/
    private static String OUT_SINGLE_PROMPT = """
            Goal:
            Fix errors with minimal changes, staying close to the original.

            Output:
            Return ONLY the improved translation.
            """;

    /**
     * 单条翻译 — 支持外部 BasePrompt
     */
    public static String buildDynamicTargetSinglePrompt(String targetLanguage, String text, String translatedText,
                                                  String termRules, String styleRules) {
        String safeText = text == null ? "" : text;
        boolean includeProtection = hasSpecialContent(safeText);
        boolean includeUnit = hasDigit(safeText);
        return buildSingleDynamicPrompt(BASE_SINGLE_PROMPT, safeText, translatedText, ModuleCodeUtils.getLanguageName(targetLanguage),
                termRules,includeProtection,includeUnit, styleRules);
    }

    private static String buildSingleDynamicPrompt(String basePrompt, String sourceText, String translateText, String targetCode
    , String termRules, boolean includeProtectionRule, boolean includeUnitRule, String styleRules) {
        StringBuilder prompt = new StringBuilder(basePrompt.trim()).append("\n\n");

        prompt.append(COMPARE_SINGLE_PROMPT.replace("{{ORIGINAL_TEXT}}", sourceText)
                .replace("{{TRANSLATION_TEXT}}", translateText)).append("\n\n");

        prompt.append(REQUIRE_SINGLE_PROMPT.replace("{{TARGET_LANGUAGE}}", targetCode)).append("\n");

        if (termRules != null && !termRules.trim().isEmpty()) {
            prompt.append(GLOSSAYR_SINGLE_PROMPT.replace("{{TERM_RULES}}", termRules.trim()).trim()).append("\n\n");
        }else {
            prompt.append("\n");
        }

        if (styleRules != null && !styleRules.trim().isEmpty()) {
            prompt.append(STYLE_RULE.replace("{{STYLE_RULES}}", styleRules.trim()).trim()).append("\n\n");
        }

        if (includeProtectionRule) {
            prompt.append(PROTECTION_RULE.trim()).append("\n\n");
        }

        if (includeUnitRule) {
            prompt.append(UNIT_RULE.trim()).append("\n\n");
        }

        prompt.append(OUT_SINGLE_PROMPT.trim());
        return prompt.toString();
    }
}
