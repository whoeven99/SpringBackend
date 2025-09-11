package com.bogdatech.utils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.bogdatech.constants.TranslateConstants.ARTICLE;
import static com.bogdatech.constants.TranslateConstants.PRODUCT;


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
     * @param target 目标语言
     * @param languagePackId 语言包
     * @param key 各类key值
     * @param customKey 用户自定义提示词
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
        if (languagePackId == null) {
            return "Translate the following HTML content to " + target  + ". Follow these rules: 1. Don't translate HTML tags; keep them as they are. 2. Translate only the visible text between HTML tags, preserving the original HTML structure and formatting. 3. Maintain all original whitespace, line breaks, and formatting; don't change the layout. 4. Do not translate or modify any emoji. 5. Output the translated HTML as plain text, no code - block wrapping (no triple backticks or language tags).";
        }
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
     * list 翻译提示词
     * @param target 目标语言
     * @param languagePackId 语言包
     * @param translationKeyType 翻译key
     * @param modelType 模型类型
     * */
    public static String getListPrompt(String target, String languagePackId, String translationKeyType, String modelType) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是一个精通翻译的小助手，你会帮我把每个语言都翻译的准确。\n")
                .append("你会自动识别源语言，并将其翻译成我指定的目标语言。\n")
                .append("你不会翻译key值，只翻译值的内容。\n")
                .append("翻译之前你会判断他是不是一个变量名，如果是变量名你就不会翻译。\n")
                .append("我会给你一个待翻译的列表，你翻译之后给我返回一个固定的返回格式。\n");

        // 1,如果有行业，才加上这一行
        if (languagePackId != null && !languagePackId.isBlank()) {
            prompt.append(String.format("翻译时你会使用适合 %s 行业的专业术语和友好语气。\n", languagePackId));
        }

        // 2,如果有 keyByModel，才加上这一行
        String keyByModel = getKeyByModel(modelType, translationKeyType);
        if (keyByModel != null && !keyByModel.isBlank()) {
            prompt.append(String.format("待翻译的数据类型是 %s，不要输出解释性文本。\n", keyByModel));
        }

        prompt.append("你给我的返回值里面的key保持我给的内容不变，后面的value是你翻译后的内容。\n")
                .append("举例说明：\n")
                .append("我给你一个列表：[\"按钮\",\"[%-S] sec%!S\", \"{{ select }}\"]\n")
                .append("再给你一个待翻译语言：Chinese (Traditional) \n")
                .append("你给我按照如下格式返回: {\n")
                .append("\"按钮\": \"按鈕\",\"[%-S] sec%!S\": \"[%-S] sec%!S\", \"{{ select }}\": \"{{ select }}\"}\n")
                .append("好，现在帮我翻译一下如下内容\n")
                .append(String.format("待翻译语言为：%s\n", target))
                .append("待翻译列表为：");

        return prompt.toString();
    }

    /**
     * 根据key和modelType生成不同的字段
     * */
    public static String getKeyByModel(String modelType, String key) {
        if (key == null || modelType == null){
            return null;
        }

        String prefix;
        switch (modelType) {
            case ARTICLE -> prefix = "article";
            case PRODUCT -> prefix = "product";
            default -> {
                return null; // modelType 不匹配
            }
        }

        return switch (key) {
            case "title" ->  prefix + " title";
//            case "body_html" -> prefix + (modelType.equals("article") ? " content" : " description");
            case "meta_title" -> prefix + " meta title";
//            case "meta_description" -> prefix + " meta description";
            default -> null;
        };
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
            String templateType,
            String brand,
            String templateStructure,
            String language,
            String contentType,
            String brandWord,
            String brandSlogan
    ) {
//        appInsights.trackTrace("productName: " + productName + " productCategory: " + productCategory + " productDescription: " + productDescription + " seoKeywords: " + seoKeywords + " image: " + image + " imageDescription: " + imageDescription + " tone: " + tone + " contentType: " + contentType + " brand: " + brand + " templateStructure: " + templateStructure + " language: " + language);
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a professional e-commerce ") ;
        prompt.append(templateType);
        prompt.append(" content creator specialized in writing ");
        prompt.append(contentType);
        prompt.append(" for Shopify. Generate high-converting, brand-aligned product descriptions that are SEO-optimized and customer-focused.\n\n");
        prompt.append("## Product Info\n");
        buildSection(prompt, buildNonNullMap(new Object[][]{
                {"Title", productName},
                {"Base Description", productDescription},
                {"Product Type", productCategory},
                {"Brand Name", brandWord},
                {"Brand Slogan", brandSlogan},
                {"SEO Keywords", seoKeywords},
        }));
        prompt.append("\n## Writing Instructions\n");
        buildSection(prompt, buildNonNullMap(new Object[][]{
                        {"Tone", tone},
                        {"Reference Brand", brand},
                        {"Structure Template", templateStructure != null ? templateStructure.trim() : ""},
                }));
//        prompt.append("Format: HTML");
        prompt.append("\n## Must-Follow Rules\n");
        prompt.append("- The entire output must be written in fluent **").append(language).append("**.\n");
        prompt.append("- Titles, slogans, CTAs, and any source content in other languages must be fully translated.\n");
        prompt.append("- No text in languages other than **").append(language).append("** should appear in the final output.\n");
        prompt.append("- All valid details from the Base Description (e.g., materials, sizes, care instructions, composition) must be retained and integrated.\n");
        prompt.append("- Structured data (e.g., tables, bullet points) must be preserved, but surrounding text should be rewritten for clarity, tone, and fluency.\n");
        prompt.append("\n**Return only the final product description wrapped in a <html> tag. No explanation or additional content.**\n");

        return prompt.toString();
    }

    /**
     * 判断动态构成提示词
     * */
    private static void buildSection(StringBuilder builder, Map<String, String> fields) {
//        appInsights.trackTrace("fields: " + fields);
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
