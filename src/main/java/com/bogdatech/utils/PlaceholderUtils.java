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
            return "Translate the following text into " + target + " using terminology and tone appropriate for the " + languagePackId + ". Detect the input language. If it is " + target + ", return the text unchanged. Otherwise, proceed as normal. Do not output any notes, annotations, explanations, corrections, or bilingual text. Even if you detect an error in the original, do not mention it—only output the final correct translation. The output should preserve the exact letter casing as the original text — do not capitalize words unless they are capitalized in the source.";
        }
        return "Translate the following text into " + target + ". Detect the input language. If it is " + target + ", return the text unchanged. Otherwise, proceed as normal. Do not output any notes, annotations, explanations, corrections, or bilingual text. Even if you detect an error in the original, do not mention it—only output the final correct translation. The output should preserve the exact letter casing as the original text — do not capitalize words unless they are capitalized in the source.";
    }

    /**
     * key值提示词
     * */
    public static String getKeyPrompt(String target, String languagePackId, String key, String customKey) {
        boolean hasLanguagePackId = languagePackId != null && !languagePackId.isEmpty();
        boolean hasCustomKey = customKey != null && !customKey.isEmpty();
        boolean hasKey = key != null && !key.isEmpty();
        if (hasLanguagePackId && hasCustomKey && hasKey) {
            //判断CustomKey里面是否包含using terminology and tone appropriate for 则使用customKey里面的文本。
            if (customKey.contains("using terminology and tone appropriate for")) {
                return "Translate the following " + key + " into " + target + " " + customKey + " Detect the input language. If it is " + target + ", return the text unchanged. Otherwise, proceed as normal. Do not output any notes, annotations, explanations, corrections, or bilingual text. Even if you detect an error in the original, do not mention it—only output the final correct translation. The output should preserve the exact letter casing as the original text — do not capitalize words unless they are capitalized in the source.";
            }
            return "Translate the following " + key + " into " + target + " using terminology and tone appropriate for the " + languagePackId + ". " + customKey + " Detect the input language. If it is " + target + ", return the text unchanged. Otherwise, proceed as normal. Do not output any notes, annotations, explanations, corrections, or bilingual text. Even if you detect an error in the original, do not mention it—only output the final correct translation. The output should preserve the exact letter casing as the original text — do not capitalize words unless they are capitalized in the source.";
        } else if (hasLanguagePackId && hasKey) {
            return "Translate the following " + key + " into " + target + " using terminology and tone appropriate for the " + languagePackId + ". Detect the input language. If it is " + target + ", return the text unchanged. Otherwise, proceed as normal. Do not output any notes, annotations, explanations, corrections, or bilingual text. Even if you detect an error in the original, do not mention it—only output the final correct translation. The output should preserve the exact letter casing as the original text — do not capitalize words unless they are capitalized in the source.";
        } else if (hasCustomKey && hasKey) {
            return "Translate the following " + key + " into " + target + ". " + customKey + " Detect the input language. If it is " + target + ", return the text unchanged. Otherwise, proceed as normal. Do not output any notes, annotations, explanations, corrections, or bilingual text. Even if you detect an error in the original, do not mention it—only output the final correct translation. The output should preserve the exact letter casing as the original text — do not capitalize words unless they are capitalized in the source.";
        }else if (!hasKey) {
            if (languagePackId != null && !languagePackId.isEmpty()){
                return "Translate the following text into " + target + " using terminology and tone appropriate for the " + languagePackId + ". Detect the input language. If it is " + target + ", return the text unchanged. Otherwise, proceed as normal. Do not output any notes, annotations, explanations, corrections, or bilingual text. Even if you detect an error in the original, do not mention it—only output the final correct translation. The output should preserve the exact letter casing as the original text — do not capitalize words unless they are capitalized in the source.";
            }
            return "Translate the following text into " + target + ". Detect the input language. If it is " + target + ", return the text unchanged. Otherwise, proceed as normal. Do not output any notes, annotations, explanations, corrections, or bilingual text. Even if you detect an error in the original, do not mention it—only output the final correct translation. The output should preserve the exact letter casing as the original text — do not capitalize words unless they are capitalized in the source.";
        }
        return "Translate the following " + key + " into " + target + ". Detect the input language. If it is " + target + ", return the text unchanged. Otherwise, proceed as normal. Do not output any notes, annotations, explanations, corrections, or bilingual text. Even if you detect an error in the original, do not mention it—only output the final correct translation. The output should preserve the exact letter casing as the original text — do not capitalize words unless they are capitalized in the source.";
    }

    /**
     * openai key 提示词
     * */
    public static String getOpenaiKeyPrompt(String target, String languagePackId, String key, String customKey) {
        boolean hasLanguagePackId = languagePackId != null && !languagePackId.isEmpty();
        boolean hasCustomKey = customKey != null && !customKey.isEmpty();
        boolean hasKey = key != null && !key.isEmpty();
        if (hasLanguagePackId && hasCustomKey && hasKey) {
            //判断CustomKey里面是否包含using terminology and tone appropriate for 则使用customKey里面的文本。
            if (customKey.contains("using terminology and tone appropriate for")) {
                return "Translate the following " + key + " into " + target + " " + customKey + " If already in " + target + ", return unchanged. Output only the final result. No comments, no source text. Preserve original casing";
            }
            return "Translate the following " + key + " into " + target + " using terminology and tone appropriate for the " + languagePackId + ". " + customKey + " If already in " + target + ", return unchanged. Output only the final result. No comments, no source text. Preserve original casing";
        } else if (hasLanguagePackId && hasKey) {
            return "Translate the following " + key + " into " + target + " using terminology and tone appropriate for the " + languagePackId + ". If already in " + target + ", return unchanged. Output only the final result. No comments, no source text. Preserve original casing";
        } else if (hasCustomKey && hasKey) {
            return "Translate the following " + key + " into " + target + ". " + customKey + " If already in " + target + ", return unchanged. Output only the final result. No comments, no source text. Preserve original casing";
        }else if (!hasKey) {
            if (languagePackId != null && !languagePackId.isEmpty()){
                return "Translate the following text into " + target + " using terminology and tone appropriate for the " + languagePackId + ". If already in " + target + ", return unchanged. Output only the final result. No comments, no source text. Preserve original casing";
            }
            return "Translate the following text into " + target + ". If already in " + target + ", return unchanged. Output only the final result. No comments, no source text. Preserve original casing";
        }
        return "Translate the following " + key + " into " + target + ". If already in " + target + ", return unchanged. Output only the final result. No comments, no source text. Preserve original casing";
    }

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
        return "Translate the following text into " + target + ". Detect the input language. If it is " + target + ", return the text unchanged. Otherwise, proceed as normal. Do not output any notes, annotations, explanations, corrections, or bilingual text. Even if you detect an error in the original, do not mention it—only output the final correct translation. The output should preserve the exact letter casing as the original text — do not capitalize words unless they are capitalized in the source.";
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
        return "Translate the following text into " + target + ". Detect the input language. If it is " + target + ", return the text unchanged. Otherwise, proceed as normal. Do not output any notes, annotations, explanations, corrections, or bilingual text. Even if you detect an error in the original, do not mention it—only output the final correct translation.";
    }

    /**
     * 完整翻译html的提示词
     * @param target 目标语言
     * @param languagePackId 语言包
     * */
    public static String getFullHtmlPrompt(String target, String languagePackId) {
        return "Translate the following HTML content to " + target + "  with " + languagePackId + " appropriate terminology and tone. Follow these rules: 1. Don't translate HTML tags; keep them as they are. 2. Translate only the visible text between HTML tags, preserving the original HTML structure and formatting. 3. Maintain all original whitespace, line breaks, and formatting; don't change the layout. 4. Do not translate or modify any emoji. 5. Output the translated HTML as plain text, no code - block wrapping (no triple backticks or language tags).";
    }

    /**
     * 政策 html提示词
     * @param target 目标语言
     * */
    public static String getPolicyPrompt(String target) {
        return "Translate the following shop policy HTML content into " + target + ". Follow these rules: 1. Don't translate HTML tags; keep them as they are. 2. Translate only the visible text between HTML tags, preserving the original HTML structure and formatting. 3. Maintain all original whitespace, line breaks, and formatting; don't change the layout. 4. Output the translated HTML as plain text, no code - block wrapping (no triple backticks or language tags).";
    }

    /**
     * 构建描述生成提示词的动态方法
     * */
    public static String buildDescriptionPrompt(
            String productName,
            String productCategory,
            String productDescription,
            String seoKeywords,
            String image,
            String imageDescription,
            String tone,
            String contentType,
            String brand,
            String templateStructure,
            String language
    ) {
//        System.out.println("productName: " + productName + " productCategory: " + productCategory + " productDescription: " + productDescription + " seoKeywords: " + seoKeywords + " image: " + image + " imageDescription: " + imageDescription + " tone: " + tone + " contentType: " + contentType + " brand: " + brand + " templateStructure: " + templateStructure + " language: " + language);
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a professional e-commerce content creator who specializes in writing high-converting product descriptions for shopify in ");
        prompt.append(language);
        prompt.append(". Please generate a product description based on the following information and structure requirements. Strictly follow the format, return only the final product description without any extra explanation.\n\n[BASIC INFORMATION]\n");
        buildSection(prompt, buildNonNullMap(new Object[][]{
                {"Product Name", productName},
                {"Product Category", productCategory},
                {"Product Description", productDescription},
                {"SEO Keywords", seoKeywords},
                {"Image", image},
                {"Image Description", imageDescription}
        }));
        prompt.append("\n[WRITING STYLE]\n");
        buildSection(prompt, buildNonNullMap(new Object[][]{
                        {"Tone", tone},
                        {"Content Type", contentType},
                        {"Brand Tone Reference", brand},
                }));
        prompt.append("\n[STRUCTURE TEMPLATE]\n");
        prompt.append(templateStructure != null ? templateStructure.trim() : "");
        return prompt.toString();
    }

    /**
     * 判断动态构成提示词
     * */
    private static void buildSection(StringBuilder builder, Map<String, String> fields) {
        System.out.println("fields: " + fields);
        fields.forEach((label, value) -> {
            if (isNotBlank(value)) {
                builder.append("- ").append(label).append(": ").append(value.trim()).append("\n");
            }
        });
    }

    private static boolean isNotBlank(String str) {
        return str != null && !str.trim().isEmpty();
    }

    public static Map<String, String> buildNonNullMap(Object[][] pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Object[] pair : pairs) {
            String key = (String) pair[0];
            String value = (String) pair[1];
            if (value != null) map.put(key, value);
        }
        return map;
    }
}
