package com.bogdatech.utils;

public class SwitchModelUtils {

    // 根据语言代码切换模型
    public static String switchModel(String languageCode) {
        return switch (languageCode) {
            case "en", "zh-CN", "de", "ja", "it", "ru", "zh-TW", "da", "nl", "id", "th", "vi", "uk", "fr", "pt-BR", "pt-PT", "ko", "hi", "bg", "cs", "el", "hr", "lt", "nb", "pl", "ro", "sk", "sv", "ar", "no" -> "qwen-plus";
            default -> "qwen-max";
        };
//        return "qwen-max";
    }
}
