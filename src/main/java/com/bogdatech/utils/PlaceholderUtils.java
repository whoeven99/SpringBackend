package com.bogdatech.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.bogdatech.constants.TranslateConstants.QWEN_MT;
import static com.bogdatech.integration.ALiYunTranslateIntegration.callWithMessage;

public class PlaceholderUtils {

    // 定义占位符的正则表达式
    private static final Pattern PLACEHOLDER_PATTERN =
            Pattern.compile("\\{\\{[^}]+\\}\\}|%\\{[^}]+\\}|\\{%[^%]+%\\}");

    /**
     * 判断文本中是否包含指定类型的占位符
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
    public static String processTextWithPlaceholders(String originalText, CharacterCountUtils countUtils, String source, String target) {
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
        String finalText = callWithMessage(QWEN_MT, textToTranslate, source, target, countUtils);
        for (String tempMarker : placeholders) {
            String originalPlaceholder = placeholderMap.get(tempMarker);
            finalText = finalText.replace(tempMarker, originalPlaceholder);
        }

        return finalText;
    }
}
