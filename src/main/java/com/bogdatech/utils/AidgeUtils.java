package com.bogdatech.utils;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class AidgeUtils {
    // 基础图片翻译的 code 输入范围
    public static Set<String> baseImageTranslateInputCodeSet = Set.of("zh", "zh-tw", "en", "fr", "it", "ja", "ko", "pt", "ru", "es", "th", "tr", "vi");

    // 基础图片翻译的 code 输出范围
    public static Set<String> baseImageTranslateOutputCodeSet = Set.of("ar", "bn", "zh", "zh-tw", "cs", "da", "nl", "en", "fi", "fr", "de", "el", "he"
            , "hu", "id", "it", "ja", "kk", "ko", "ms", "pl", "pt", "ru", "es", "sv", "th", "tl", "tr", "uk", "ur", "vi");

    // shopifyCode 与 aigdeCode 的映射关系
    public static final Map<String, String> SHOPIFY_TO_AIGDE = Map.ofEntries(
            Map.entry("zh-CN", "zh"),
            Map.entry("zh-TW", "zh-tw")
    );

    // 做aidge的特殊语言校验
    public static boolean isBaseImageTranslateInputCode(String sourceCode, String targetCode) {
        return switch (targetCode) {
            case "zh-tw" ->  // 繁体中文
                    "en".equals(sourceCode) || "zh".equals(sourceCode);
            case "el" ->     // 希腊语
                    "tr".equals(sourceCode) || "en".equals(sourceCode);
            case "kk" ->     // 哈萨克语
                    "zh".equals(sourceCode);
            default -> true;
        };
    }

    // 校验是否是标准版的code范围
    public static boolean isBaseImageTranslateInputCodeRange(String sourceCode, String targetCode) {
        return baseImageTranslateInputCodeSet.contains(sourceCode) && baseImageTranslateOutputCodeSet.contains(targetCode);
    }


}
