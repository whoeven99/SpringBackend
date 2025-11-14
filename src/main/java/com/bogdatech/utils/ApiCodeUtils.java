package com.bogdatech.utils;

import java.util.*;

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

    //火山代码处理
    public static String huoShanTransformCode(String code) {
        return switch (code) {
            case "ak", "as", "bm", "eu", "be", "br", "ce", "fy", "yi", "uz", "to", "tg", "si", "sd", "ii", "ug", "su", "rn", "rm", "kw", "fo", "fil", "dz", "ff", "is", "ia", "ga", "jv", "kl", "ks", "kk", "ku", "ky", "lb", "mt", "mg", "gv", "mi", "ne", "se", "nb", "nn", "or", "os", "ps", "pt-BR", "pt-PT", "sa", "sc", "gd" ->
                    "#N/A";
            case "zh-CN" -> "zh"; // 简体中文
            case "zh-TW" -> "zh-Hant"; // 繁体中文
            default -> code;
        };
    }

    //千问mt语言代码处理
    public static String qwenMtCode(String code) {
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

    //根据语言代码获取语言的全称
    private static final Map<String, String> languageMap = new HashMap<>();

    static {
        languageMap.put("af", "Afrikaans");
        languageMap.put("ak", "Akan");
        languageMap.put("sq", "Albanian");
        languageMap.put("am", "Amharic");
        languageMap.put("ar", "Arabic");
        languageMap.put("hy", "Armenian");
        languageMap.put("as", "Assamese");
        languageMap.put("az", "Azerbaijani");
        languageMap.put("bm", "Bambara");
        languageMap.put("bn", "Bangla");
        languageMap.put("eu", "Basque");
        languageMap.put("be", "Belarusian");
        languageMap.put("bs", "Bosnian");
        languageMap.put("br", "Breton");
        languageMap.put("bg", "Bulgarian");
        languageMap.put("my", "Burmese");
        languageMap.put("ca", "Catalan");
        languageMap.put("ckb", "Central Kurdish");
        languageMap.put("ce", "Chechen");
        languageMap.put("zh-CN", "Chinese (Simplified)");
        languageMap.put("zh-TW", "Chinese (Traditional)");
        languageMap.put("kw", "Cornish");
        languageMap.put("hr", "Croatian");
        languageMap.put("cs", "Czech");
        languageMap.put("da", "Danish");
        languageMap.put("nl", "Dutch");
        languageMap.put("dz", "Dzongkha");
        languageMap.put("en", "English");
        languageMap.put("eo", "Esperanto");
        languageMap.put("et", "Estonian");
        languageMap.put("ee", "Ewe");
        languageMap.put("fo", "Faroese");
        languageMap.put("fil", "Filipino");
        languageMap.put("fi", "Finnish");
        languageMap.put("fr", "French");
        languageMap.put("ff", "Fulah");
        languageMap.put("gl", "Galician");
        languageMap.put("lg", "Ganda");
        languageMap.put("ka", "Georgian");
        languageMap.put("de", "German");
        languageMap.put("el", "Greek");
        languageMap.put("gu", "Gujarati");
        languageMap.put("ha", "Hausa");
        languageMap.put("he", "Hebrew");
        languageMap.put("hi", "Hindi");
        languageMap.put("hu", "Hungarian");
        languageMap.put("is", "Icelandic");
        languageMap.put("ig", "Igbo");
        languageMap.put("id", "Indonesian");
        languageMap.put("ia", "Interlingua");
        languageMap.put("ga", "Irish");
        languageMap.put("it", "Italian");
        languageMap.put("ja", "Japanese");
        languageMap.put("jv", "Javanese");
        languageMap.put("kl", "Kalaallisut");
        languageMap.put("kn", "Kannada");
        languageMap.put("ks", "Kashmiri");
        languageMap.put("kk", "Kazakh");
        languageMap.put("km", "Khmer");
        languageMap.put("ki", "Kikuyu");
        languageMap.put("rw", "Kinyarwanda");
        languageMap.put("ko", "Korean");
        languageMap.put("ku", "Kurdish");
        languageMap.put("ky", "Kyrgyz");
        languageMap.put("lo", "Lao");
        languageMap.put("lv", "Latvian");
        languageMap.put("ln", "Lingala");
        languageMap.put("lt", "Lithuanian");
        languageMap.put("lu", "Luba-Katanga");
        languageMap.put("lb", "Luxembourgish");
        languageMap.put("mk", "Macedonian");
        languageMap.put("mg", "Malagasy");
        languageMap.put("ms", "Malay");
        languageMap.put("ml", "Malayalam");
        languageMap.put("mt", "Maltese");
        languageMap.put("gv", "Manx");
        languageMap.put("mi", "Māori");
        languageMap.put("mr", "Marathi");
        languageMap.put("mn", "Mongolian");
        languageMap.put("ne", "Nepali");
        languageMap.put("nd", "North Ndebele");
        languageMap.put("se", "Northern Sami");
        languageMap.put("no", "Norwegian");
        languageMap.put("nb", "Norwegian (Bokmål)");
        languageMap.put("nn", "Norwegian Nynorsk");
        languageMap.put("or", "Odia");
        languageMap.put("om", "Oromo");
        languageMap.put("os", "Ossetic");
        languageMap.put("ps", "Pashto");
        languageMap.put("fa", "Persian");
        languageMap.put("pl", "Polish");
        languageMap.put("pt-BR", "Portuguese (Brazil)");
        languageMap.put("pt-PT", "Portuguese (Portugal)");
        languageMap.put("pa", "Punjabi");
        languageMap.put("qu", "Quechua");
        languageMap.put("ro", "Romanian");
        languageMap.put("rm", "Romansh");
        languageMap.put("rn", "Rundi");
        languageMap.put("ru", "Russian");
        languageMap.put("sg", "Sango");
        languageMap.put("sa", "Sanskrit");
        languageMap.put("sc", "Sardinian");
        languageMap.put("gd", "Scottish Gaelic");
        languageMap.put("sr", "Serbian (Latin)");
        languageMap.put("sn", "Shona");
        languageMap.put("ii", "Sichuan Yi");
        languageMap.put("sd", "Sindhi");
        languageMap.put("si", "Sinhala");
        languageMap.put("sk", "Slovak");
        languageMap.put("sl", "Slovenian");
        languageMap.put("so", "Somali");
        languageMap.put("es", "Spanish");
        languageMap.put("su", "Sundanese");
        languageMap.put("sw", "Swahili");
        languageMap.put("sv", "Swedish");
        languageMap.put("tg", "Tajik");
        languageMap.put("ta", "Tamil");
        languageMap.put("tt", "Tatar");
        languageMap.put("te", "Telugu");
        languageMap.put("th", "Thai");
        languageMap.put("bo", "Tibetan");
        languageMap.put("ti", "Tigrinya");
        languageMap.put("to", "Tongan");
        languageMap.put("tr", "Turkish");
        languageMap.put("tk", "Turkmen");
        languageMap.put("uk", "Ukrainian");
        languageMap.put("ur", "Urdu");
        languageMap.put("ug", "Uyghur");
        languageMap.put("uz", "Uzbek");
        languageMap.put("vi", "Vietnamese");
        languageMap.put("cy", "Welsh");
        languageMap.put("fy", "Western Frisian");
        languageMap.put("wo", "Wolof");
        languageMap.put("xh", "Xhosa");
        languageMap.put("yi", "Yiddish");
        languageMap.put("yo", "Yoruba");
        languageMap.put("zu", "Zulu");
    }

    public static String getLanguageName(String isoCode) {
        return languageMap.getOrDefault(isoCode, isoCode);
    }

    // ali图片code
    public static Map<String, Set<String>> aliImageMap = new HashMap<>();
    static {
        aliImageMap.put("zh", new HashSet<>(Arrays.asList("en", "ru", "es", "fr", "de", "it", "nl", "pt", "vi", "tr"
                , "ms", "zh-tw", "th", "pl", "id", "ja", "ko")));
        aliImageMap.put("en", new HashSet<>(Arrays.asList("zh", "ru", "es", "fr", "de", "it", "pt", "vi", "tr"
                , "ms", "th", "pl", "id", "ja", "ko")));
    }

    public static boolean isAliImageSupport(String sourceCode, String targetCode) {
        return switch (sourceCode) {
            case "zh" ->
                    aliImageMap.get("zh").contains(targetCode);
            case "en" ->
                    aliImageMap.get("en").contains(targetCode);
            default -> false;
        };
    }
}
