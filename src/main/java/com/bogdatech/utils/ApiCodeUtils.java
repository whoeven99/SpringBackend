package com.bogdatech.utils;

public class ApiCodeUtils {

    //微软代码处理
    public static String microsoftTransformCode(String code) {
        return switch (code) {
            case "zh-CN" -> "zh-Hans"; // 简体中文
            case "zh-TW" -> "zh-Hant"; // 繁体中文
            case "mn" -> "mn-Cyrl"; // 蒙古文（西里尔字母）
            case "pt-BR" -> "pt-br"; // 巴西葡萄牙语
            case "pt-PT" -> "pt-pt"; // 欧洲葡萄牙语
            case "sr" -> "sr-Cyrl"; // 塞尔维亚文（西里尔字母）
            case "rn" -> "run"; // 奥罗莫语
            default -> code;
        };
    }

    //阿里云代码处理
    public static String aliyunTransformCode(String code) {
        return switch (code) {
            case "zh-CN" -> "zh"; // 简体中文
            case "zh-TW" -> "zh-tw"; // 繁体中文
            case "ckb", "ce", "hr", "ff", "ki", "dz", "lu", "nd", "nb", "nn", "os", "ps", "pt-BR", "pt-PT", "sc", "gd", "sr", "ii", "bo", "uk", "ug" -> "#N/A";
            default -> code;
        };
    }

    //火山代码处理
    public static String huoShanTransformCode(String code) {
        return switch (code) {
            case "ak", "as", "bm", "eu", "be", "br", "ce", "fy", "yi", "uz", "to", "tg", "si", "sd", "ii", "ug", "su", "rn", "rm", "kw", "fo", "fil", "dz", "ff", "is", "ia", "ga", "jv", "kl", "ks", "kk", "ku", "ky", "lb", "mt", "mg", "gv", "mi", "ne", "se", "nb", "nn", "or", "os", "ps", "pt-BR", "pt-PT", "sa", "sc", "gd" -> "#N/A";
            case "zh-CN" -> "zh"; // 简体中文
            case "zh-TW" -> "zh-Hant"; // 繁体中文
            default -> code;
        };
    }

    //千问mt语言代码处理
    public static String qwenMtCode(String code){
        return switch (code) {
            case "zh-CN" -> "Chinese"; // 简体中文
            case "en" -> "English"; // 英文
            case "ja" -> "Japanese"; // 日语
            case "ko" -> "Korean"; // 韩语
            case "th" -> "Thai"; // 泰语
            case "fr" -> "French"; // 法语
            case "de" -> "German"; // 德语
            case "es" -> "Spanish"; // 西班牙语
            case "ar" -> "Arabic"; // 阿拉伯语
            case "id" -> "Indonesian"; // 印度尼西亚语
            case "vi" -> "Vietnamese"; // 越南语
            case "pt-BR" -> "Portuguese"; // 巴西葡萄牙语
            case "it" -> "Italian"; // 意大利语
            case "nl" -> "Dutch"; // 荷兰语
            case "ru" -> "Russian"; // 俄语
            case "km" -> "Khmer"; // 高棉语
            case "cs" -> "Czech"; // 捷克语
            case "pl" -> "Polish"; // 波兰语
            case "fa" -> "Persian"; // 波斯语
            case "he" -> "Hebrew"; // 希伯来语
            case "tr" -> "Turkish"; // 土er其语
            case "hi" -> "Hindi"; // 印地语
            case "bn" -> "Bengali"; // 孟加拉语
            case "ur" -> "Urdu"; // 乌尔都语
            default -> code;
        };
    }

    public static boolean isDatabaseLanguage(String languageCode) {
        return switch (languageCode) {
            case "en", "es", "fr", "de", "pt-BR", "pt-PT", "zh-CN", "zh-TW", "ja", "it", "ru", "ko", "nl", "da", "hi", "bg", "cs", "el", "fi", "hr", "hu", "id", "lt", "nb", "pl", "ro", "sk", "sl", "sv", "th", "tr", "vi", "ar", "no", "uk", "lv", "et" ->
                    true;  // 语言代码有效
            default -> false; // 语言代码无效
        };
    }
}
