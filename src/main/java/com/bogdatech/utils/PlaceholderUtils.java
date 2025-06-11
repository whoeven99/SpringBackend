package com.bogdatech.utils;

import com.bogdatech.model.controller.request.TranslateRequest;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.bogdatech.constants.TranslateConstants.QWEN_MT;
import static com.bogdatech.constants.UserPrivateConstants.GOOGLE;
import static com.bogdatech.integration.ALiYunTranslateIntegration.callWithMessage;
import static com.bogdatech.integration.TranslateApiIntegration.getGoogleTranslationWithRetry;

public class PlaceholderUtils {

    // 定义占位符的正则表达式
    private static final Pattern PLACEHOLDER_PATTERN =
            Pattern.compile("\\{\\{[^}]+\\}\\}|\\{\\s*[^}]*\\s*\\}|%\\{[^}]+\\}|\\{%(.*?)%\\}|\\[[^\\]]+\\]|\\{\\}");

    /**
     * 判断文本中是否包含指定类型的占位符
     *
     * @param text 输入的文本
     * @return 如果包含任意一种占位符返回 true，否则返回 false
     */
    public static boolean hasPlaceholders(String text) {
        // 检查输入是否为空
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        // 使用正则表达式匹配
        return PLACEHOLDER_PATTERN.matcher(text).find();
    }

    /**
     * 处理文本：提取占位符，翻译剩余文本，然后替换回占位符
     */
    public static String processTextWithPlaceholders(String originalText, CharacterCountUtils countUtils, String mtSource, String mtTarget, String mode, String source, String target) {
        if (originalText == null || originalText.trim().isEmpty()) {
            return originalText;
        }

        // 存储占位符及其临时标记
        Map<String, String> placeholderMap = new HashMap<>();
        List<String> placeholders = new ArrayList<>();
        int placeholderCount = 0;

        // 提取占位符并替换为临时标记
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(originalText);
        StringBuffer processedText = new StringBuffer();

        while (matcher.find()) {
            String placeholder = matcher.group();
            String tempMarker = "#_" + placeholderCount++ + "#";
            placeholderMap.put(tempMarker, placeholder);
            placeholders.add(tempMarker);
            matcher.appendReplacement(processedText, tempMarker);
        }
        matcher.appendTail(processedText);

        // 获取不含占位符的文本
        String textToTranslate = processedText.toString();

        // 调用翻译服务
        // 将占位符替换回去
        String finalText = null;
        switch (mode) {
            case QWEN_MT:
                finalText = callWithMessage(QWEN_MT, textToTranslate, mtSource, mtTarget, countUtils);
                break;
            case GOOGLE:
                finalText = getGoogleTranslationWithRetry(new TranslateRequest(0, null, null, source, target, textToTranslate));
                break;
        }
//        String finalText = callWithMessage(QWEN_MT, textToTranslate, source, target, countUtils);
        if (finalText == null) {
            return null;
        }
        for (String tempMarker : placeholders) {
            String originalPlaceholder = placeholderMap.get(tempMarker);
            finalText = finalText.replace(tempMarker, originalPlaceholder);
        }

        return finalText;
    }

    /**
     * 根据变量前缀，输出变量全值
     *
     * @param placeholder 变量
     * @return 变量的全值
     **/
    private static String getOuterMarker(String placeholder) {
        if (placeholder.startsWith("{{")) {
            return "{{ }}";
        } else if (placeholder.startsWith("%{")) {
            return "%{ }";
        } else if (placeholder.startsWith("{%")) {
            return "{% %}";
        } else if (placeholder.startsWith("[")) {
            return "[ ]";
        } else if (placeholder.startsWith("[{{")) {
            return "[{{ }}]";
        } else if (placeholder.startsWith("{")) {
            return "{ }";
        }
        return "";
    }

    /**
     * 根据变量前缀，输出变量全值，将所有的变量全值拼接起来
     * 目前用简单的 + 拼接，后续可以优化为使用 StringBuffer
     *
     * @param input 变量
     * @return 拼接所有变量的全值
     **/
    public static String getOuterString(String input) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(input);
        // 使用 Set 去重外层标记
        Set<String> uniqueOuterMarkers = new HashSet<>();
        String output = "";
        while (matcher.find()) {
            String placeholder = matcher.group(); // 完整的占位符，如 {{name}}
            String outerMarker = getOuterMarker(placeholder); // 提取外层标记，如 {{}}
            uniqueOuterMarkers.add(outerMarker);
        }
        int i = 0;
        for (String outerMarker : uniqueOuterMarkers) {
            if (i == 0) {
                i++;
                output = output + outerMarker;
            } else {
                output = output + "," + outerMarker;
            }

        }
        return output;
    }

    /**
     * 极简提示词
     *
     * @param target         目标语言
     * @param languagePackId 语言包
     * @return 极简提示词
     */
    public static String getSimplePrompt(String target, String languagePackId){
        if (languagePackId != null && !languagePackId.isEmpty()){
            return "Translate the following text into " + target + " using terminology and tone appropriate for the " + languagePackId + ". If it is determined that the text does not need to be translated, please output the original text. Do not output any notes, annotations, explanations, corrections, or bilingual text. Even if you detect an error in the original, do not mention it—only output the final correct translation.";
        }
        return "Translate the following text into " + target + ". If it is determined that the text does not need to be translated, please output the original text. Do not output any notes, annotations, explanations, corrections, or bilingual text. Even if you detect an error in the original, do not mention it—only output the final correct translation.";
    }
//    public static String getSimplePrompt(String target, String languagePackId, String sourceText) {
//        if (languagePackId != null && !languagePackId.isEmpty()) {
//            return "You are a professional e-commerce translator specializing in localized marketing for " + target + " with appropriate tone and terminology for the " + languagePackId + " category. Detect the language. If it's " + target + ", return it unchanged. Otherwise, translate into fluent, high-conversion " + target + ". Guidelines: 1. Tone: concise and persuasive; 2. Brand comes first and stays untranslated; 3. Use popular Google/Amazon search terms; 4. Keep original capitalization; 5. Do not: translate literally, use obscure words, add extra content, or output any language other than " + target + ". Return only the final optimized version. Original: " + sourceText;
//        }
//        return "You are a professional e-commerce translator specializing in localized marketing for " + target + ". Detect the language. If it's " + target + ", return it unchanged. Otherwise, translate into fluent, high-conversion " + target + ". Guidelines: 1. Tone: concise and persuasive; 2. Brand comes first and stays untranslated; 3. Use popular Google/Amazon search terms; 4. Keep original capitalization; 5. Do not: translate literally, use obscure words, add extra content, or output any language other than " + target + ". Return only the final optimized version. Original: " + sourceText;
//    }

    /**
     * 变量提示词
     *
     * @param target         目标语言
     * @param variables      变量
     * @param languagePackId 语言包
     * @return 变量提示词
     */
    public static String getVariablePrompt(String target, String variables, String languagePackId) {
        if (languagePackId != null && !languagePackId.isEmpty()) {
            return "Translate the following text into " + target + " using terminology and tone appropriate for the " + languagePackId + ". Do not translate any content enclosed in " + variables + " —these are variable placeholders and must remain exactly as they are. Output only the translated text.";
        }
        return "Translate the following text into " + target + ". Do not translate any content enclosed in " + variables + " —these are variable placeholders and must remain exactly as they are. Output only the translated text.";
    }

    /**
     * 词汇表的提示词
     *
     * @param target         目标语言
     * @param glossary       词汇表
     * @param languagePackId 语言包
     * @return 词汇表的提示词
     */
    public static String getGlossaryPrompt(String target, String glossary, String languagePackId) {
        if (languagePackId != null && !languagePackId.isEmpty()) {
            return "Translate the following text into " + target + " using terminology and tone appropriate for the " + languagePackId + ", using the specified translations for certain words (e.g.," + glossary + "). Output only the translated text.";
        }
        return "Translate the following text into " + target + ", using the specified translations for certain words (e.g.," + glossary + "). Output only the translated text.";
    }

    /**
     * handle类型的提示词
     *
     * @param target 目标语言
     * @return handle类型的提示词
     */
    public static String getHandlePrompt(String target) {
        return "Translate each word in the following handle into " + target + ". Keep the '-' separators as they are. Output only the translated handle.";
    }


    /**
     * handle类型的提示词
     *
     * @return handle类型的提示词
     */
    public static String getCategoryPrompt() {
        return "Based on the following website description, return which category of e-commerce website this belongs to. Only return the category name.";
    }

    /**
     * 5字符内的数据文本翻译
     * qwen-max 专用提示词
     */
    public static String getShortPrompt(String target) {
        return "Translate the following text into " + target + ". If it is determined that the text does not need to be translated, please output the original text. Do not output any notes, annotations, explanations, corrections, or bilingual text. Even if you detect an error in the original, do not mention it—only output the final correct translation.";
    }
}
