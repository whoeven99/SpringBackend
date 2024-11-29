package com.bogdatech.utils;

public class ApiCodeUtils {
    public static final String OS = "os";

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

    public static String aliyunTransformCode(String code) {
        return switch (code) {
            case "zh-CN" -> "zh"; // 简体中文
            case "zh-TW" -> "zh-tw"; // 繁体中文
            case "ckb", "ce", "hr", "ff", "ki", "dz", "lu", "nd", "nb", "nn", "os", "ps", "pt-BR", "pt-PT", "sc", "gd", "sr", "ii", "bo", "uk", "ug" -> "#N/A";
            default -> code;
        };
    }
}
