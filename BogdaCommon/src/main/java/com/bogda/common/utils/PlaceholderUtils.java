package com.bogda.common.utils;

import java.util.LinkedHashMap;
import java.util.Map;

public class PlaceholderUtils {
    /**
     * handle类型的提示词
     */
    public static String getHandlePrompt(String target) {
        return "Translate the following text into " + target + ". Detect the input language. If it is " + target + ", return the text unchanged. Otherwise, proceed as normal. Do not output any notes, annotations, explanations, corrections, or bilingual text. Even if you detect an error in the original, do not mention it—only output the final correct translation. The output should preserve the exact letter casing as the original text — do not capitalize words unless they are capitalized in the source.";
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
//        AppInsightsUtils.trackTrace("productName: " + productName + " productCategory: " + productCategory + " productDescription: " + productDescription + " seoKeywords: " + seoKeywords + " image: " + image + " imageDescription: " + imageDescription + " tone: " + tone + " contentType: " + contentType + " brand: " + brand + " templateStructure: " + templateStructure + " language: " + language);
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
        fields.forEach((label, value) -> {
            if (value != null && !value.isEmpty()) {
                builder.append("- ").append(label).append(": ").append(value.trim()).append("\n");
            }
        });
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
