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
            Pattern.compile("\\{\\{[^}]+\\}\\}|\\{\\w+\\}|%\\{[^}]+\\}|\\{%(.*?)%\\}|\\[[^\\]]+\\]");

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
     *根据变量前缀，输出变量全值
     * @param placeholder 变量
     * @return 变量的全值
     * **/
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
     * @param input 变量
     * @return 拼接所有变量的全值
     * **/
    public static String getOuterString(String input){
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
            if (i == 0){
                i++;
                output = output + outerMarker;
            }else {
                output = output + "," + outerMarker;
            }

        }
        return output;
    }

    /**
     * 极简提示词
     * @param target 目标语言
     * @return 极简提示词
     * */
    public static String getSimplePrompt(String target){
        return "Translate the following text into " + target + ".Output only the translated text";
    }

    /**
     * 变量提示词
     * @param target 目标语言
     * @param variables 变量
     * @return 变量提示词
     * */
    public static String getVariablePrompt(String target, String variables){
        return "Translate the following text into " + target + ", keeping variables like " + variables + " untranslated. Output only the translated text.";
    }

    /**
     * 词汇表的提示词
     * @param target 目标语言
     * @param glossary 词汇表
     * @return 词汇表的提示词
     * */
    public static String getGlossaryPrompt(String target, String glossary){
        return "Translate the following text into " + target + ", using the specified translations for certain words (e.g.," + glossary + "). Output only the translated text.";
    }

    /**
     * 根据type类型选择提示词
     * @param type 类型
     * @param target 目标语言
     * @param variable 变量和词汇表数据
     * @return 提示词
     */
    public static String getPrompt(String type, String target, String variable) {
        return switch (type) {
            case "variables" -> getVariablePrompt(target, variable);
            case "glossary" -> getGlossaryPrompt(target, variable);
            default -> getSimplePrompt(target);
        };
    }
}
