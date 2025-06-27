package com.bogdatech.utils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


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
            return "Translate the following text into " + target + " using terminology and tone appropriate for the " + languagePackId + ". If the text is already written in " + target + ", do not translate it—just output it exactly as it is. Do not output any notes, annotations, explanations, corrections, or bilingual text. Even if you detect an error in the original, do not mention it—only output the final correct translation. The output should preserve the exact letter casing as the original text — do not capitalize words unless they are capitalized in the source.";
        }
        return "Translate the following text into " + target + ". If the text is already written in " + target + ", do not translate it—just output it exactly as it is. Do not output any notes, annotations, explanations, corrections, or bilingual text. Even if you detect an error in the original, do not mention it—only output the final correct translation. The output should preserve the exact letter casing as the original text — do not capitalize words unless they are capitalized in the source.";
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
            return "Translate the following text into " + target + " using terminology and tone appropriate for the " + languagePackId + ". Do not translate any content enclosed in " + variables + " —these are variable placeholders and must remain exactly as they are. Output only the translated text. The output should preserve the exact letter casing as the original text — do not capitalize words unless they are capitalized in the source.";
        }
        return "Translate the following text into " + target + ". Do not translate any content enclosed in " + variables + " —these are variable placeholders and must remain exactly as they are. Output only the translated text. The output should preserve the exact letter casing as the original text — do not capitalize words unless they are capitalized in the source.";
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
            return "Translate the following text into " + target + " using terminology and tone appropriate for the " + languagePackId + ", using the specified translations for certain words (e.g.," + glossary + "). Output only the translated text. The output should preserve the exact letter casing as the original text — do not capitalize words unless they are capitalized in the source.";
        }
        return "Translate the following text into " + target + ", using the specified translations for certain words (e.g.," + glossary + "). Output only the translated text. The output should preserve the exact letter casing as the original text — do not capitalize words unless they are capitalized in the source.";
    }

    /**
     * handle类型的提示词
     *
     * @param target 目标语言
     * @return handle类型的提示词
     */
    public static String getHandlePrompt(String target) {
        return "Translate the following text into " + target + ". If the text is already written in " + target + ", do not translate it—just output it exactly as it is. Do not output any notes, annotations, explanations, corrections, or bilingual text. Even if you detect an error in the original, do not mention it—only output the final correct translation. The output should preserve the exact letter casing as the original text — do not capitalize words unless they are capitalized in the source.";
//        return "Translate each word in the following handle into " + target + ". Keep the '-' separators as they are. Output only the translated handle.";
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
        return "Translate the following text into " + target + ". If the text is already written in " + target + ", do not translate it—just output it exactly as it is. Do not output any notes, annotations, explanations, corrections, or bilingual text. Even if you detect an error in the original, do not mention it—only output the final correct translation.";
    }

    /**
     * 完整翻译html的提示词
     * @param target 目标语言
     * @param languagePackId 语言包
     * */
    public static String getFullHtmlPrompt(String target, String languagePackId) {
        return "Translate the following HTML content to " + target + "  with " + languagePackId + " appropriate terminology and tone. Follow these rules: 1. Don't translate HTML tags; keep them as they are. 2. Translate only the visible text between HTML tags, preserving the original HTML structure and formatting. 3. Maintain all original whitespace, line breaks, and formatting; don't change the layout. 4. Output the translated HTML as plain text, no code - block wrapping (no triple backticks or language tags).";
    }
}
