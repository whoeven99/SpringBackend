package com.bogda.common.utils;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class PictureUtils {
    // aidge基础图片翻译的 code 输入范围
    public static Set<String> aidgeImageTranslateInputCodeSet = Set.of("zh", "zh-tw", "en", "fr", "it", "ja", "ko", "pt", "ru", "es", "th", "tr", "vi");

    // aidge基础图片翻译的 code 输出范围
    public static Set<String> aidgeImageTranslateOutputCodeSet = Set.of("ar", "bn", "zh", "zh-tw", "cs", "da", "nl", "en", "fi", "fr", "de", "el", "he"
            , "hu", "id", "it", "ja", "kk", "ko", "ms", "pl", "pt", "ru", "es", "sv", "th", "tl", "tr", "uk", "ur", "vi");

    // 火山机器翻译支持的输入code范围
    public static Set<String> huoShanImageTranslateInputCodeSet = Set.of("bs", "et", "lt", "ta", "lv", "sl", "ms", "mr", "ml",
            "sk", "az", "bn", "cs", "da", "de", "en", "es", "fi", "fr", "gu", "hi", "hr", "id", "it", "ja", "ko", "nl"
            , "no", "pa", "pl", "pt", "ru", "sv", "th", "vi", "zh", "zh-Hant"
    );

    // 火山机器翻译支持的输出code范围
    public static Set<String> huoShanImageTranslateOutputCodeSet = Set.of("zh", "en", "pt", "fr", "de", "id", "nl", "it"
            , "tr", "ru", "pl", "fi", "ro", "cs", "el", "uk", "sv", "ms", "no", "sk", "mk", "lv", "tl", "mn", "lt", "hr"
            , "et", "bs", "da", "bg", "af", "ja", "ko", "zh-Hant", "th", "hi", "mr", "te", "ta", "my", "ml", "km", "kn"
            , "he", "bn", "ka");

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

    /**
     * 校验不同模型的语言范围
     */
    public static boolean isDifferentImageTranslateInputCode(String sourceCode, String targetCode, int model) {
        if (model == 1) {
            boolean baseImageTranslateInputCodeRange = aidgeImageTranslateInputCodeSet.contains(sourceCode) && aidgeImageTranslateOutputCodeSet.contains(targetCode);
            boolean baseImageTranslateInputCode = isBaseImageTranslateInputCode(sourceCode, targetCode);
            return baseImageTranslateInputCodeRange && baseImageTranslateInputCode;
        }

        if (model == 2) {
            return huoShanImageTranslateInputCodeSet.contains(sourceCode) && huoShanImageTranslateOutputCodeSet.contains(targetCode);
        }

        return false;
    }

    /**
     * 火山机器翻译支持的图片类型
     */
    public static final Set<String> HUO_SHAN_IMAGE_TYPE = new HashSet<>(Arrays.asList("png", "jpg"));

    /**
     *  aidge支持的图片类型
     */
    public static final Set<String> AIGDE_IMAGE_TYPE = new HashSet<>(Arrays.asList("png", "jpg", "jpeg", "bmp", "webp"));

    // 判断传入的模型和图片类型是否支持
    public static boolean isSupportModelAndImageType(String imageType, int model) {
        if (model == 1) {
            return AIGDE_IMAGE_TYPE.contains(imageType);
        } else if (model == 2) {
            return HUO_SHAN_IMAGE_TYPE.contains(imageType);
        }
        return false;
    }

    /**
     * 获取url的后缀
     */
    public static String getExtensionFromUrl(String url) {
        // 去除 URL 参数，如 ?v=123456
        String cleanUrl = url.split("\\?")[0];

        // 获取最后一个点后的内容
        int lastDotIndex = cleanUrl.lastIndexOf(".");
        if (lastDotIndex != -1 && lastDotIndex < cleanUrl.length() - 1) {
            return cleanUrl.substring(lastDotIndex + 1);
        }

        return null;
    }

}
