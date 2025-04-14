package com.bogdatech.utils;

import java.util.HashSet;
import java.util.Set;

public class JudgeTranslateUtils {

    // 明确不翻译的key集合
    private static final Set<String> NO_TRANSLATE_KEYS = new HashSet<>();

    static {
        NO_TRANSLATE_KEYS.add("general.rtl_languages");
        NO_TRANSLATE_KEYS.add("general.custom_css");
        NO_TRANSLATE_KEYS.add("general.link_google_font");
        NO_TRANSLATE_KEYS.add("shopify.checkout.order_summary.shipping_pending_value");
        NO_TRANSLATE_KEYS.add("customer_accounts.order_details.no_data_provided");
    }

    // 包含.json时不翻译的子字符串集合
    private static final Set<String> JSON_NO_TRANSLATE_SUBSTRINGS = new HashSet<>();

    static {
        JSON_NO_TRANSLATE_SUBSTRINGS.add("order_number");
        JSON_NO_TRANSLATE_SUBSTRINGS.add("custom_color");
        JSON_NO_TRANSLATE_SUBSTRINGS.add("padding");
        JSON_NO_TRANSLATE_SUBSTRINGS.add("margin");
        JSON_NO_TRANSLATE_SUBSTRINGS.add("height");
        JSON_NO_TRANSLATE_SUBSTRINGS.add("width");
        JSON_NO_TRANSLATE_SUBSTRINGS.add("checksum");
        JSON_NO_TRANSLATE_SUBSTRINGS.add("general.discount_rate");
        JSON_NO_TRANSLATE_SUBSTRINGS.add("font");
        JSON_NO_TRANSLATE_SUBSTRINGS.add("templates.404.subtext");
        JSON_NO_TRANSLATE_SUBSTRINGS.add("date_formats");
        JSON_NO_TRANSLATE_SUBSTRINGS.add("css");
    }

    // value包含px时不翻译的key子字符串集合
    private static final Set<String> PX_NO_TRANSLATE_SUBSTRINGS = new HashSet<>();

    static {
        PX_NO_TRANSLATE_SUBSTRINGS.add(".mg");
        PX_NO_TRANSLATE_SUBSTRINGS.add("max_with");
        PX_NO_TRANSLATE_SUBSTRINGS.add("radius_image");
        PX_NO_TRANSLATE_SUBSTRINGS.add(".pd");
        PX_NO_TRANSLATE_SUBSTRINGS.add("zindex");
        PX_NO_TRANSLATE_SUBSTRINGS.add("section.index.json");
        PX_NO_TRANSLATE_SUBSTRINGS.add("swatch");
        PX_NO_TRANSLATE_SUBSTRINGS.add("not_applicable");
        PX_NO_TRANSLATE_SUBSTRINGS.add("image.json");
        PX_NO_TRANSLATE_SUBSTRINGS.add("wborder");
    }

    //key中包含以下字符不翻译（原先的逻辑）
    private static final Set<String> OLD_NO_TRANSLATE = new HashSet<>();

    static {
        OLD_NO_TRANSLATE.add("metafield:");
        OLD_NO_TRANSLATE.add("color");
        OLD_NO_TRANSLATE.add("formId:");
        OLD_NO_TRANSLATE.add("phone_text");
        OLD_NO_TRANSLATE.add("email_text");
        OLD_NO_TRANSLATE.add("carousel_easing");
        OLD_NO_TRANSLATE.add("_link");
        OLD_NO_TRANSLATE.add("general.rtl");
        OLD_NO_TRANSLATE.add("css:");
        OLD_NO_TRANSLATE.add("icon:");
    }

    /**
     * 判断给定的key是否需要翻译
     *
     * @param key   要检查的key
     * @param value 对应的value，可能为null
     * @return true表示需要翻译，false表示不需要翻译
     */
    public static boolean shouldTranslate(String key, String value) {
        if (key.equals("handle")){
            return false;
        }

        //第一步： 检查是否为不翻译的key
        for (String substring : OLD_NO_TRANSLATE) {
            if (key.contains(substring)) {
                return false;
            }
        }

        // 第二步：检查是否为明确不翻译的key
        for (String substring : NO_TRANSLATE_KEYS) {
            if (key.contains(substring)) {
                return false;
            }
        }

        // 第三步：检查包含.json的key
        if (key.contains(".json")) {
            for (String substring : JSON_NO_TRANSLATE_SUBSTRINGS) {
                if (key.contains(substring)) {
                    return false;
                }
            }
        }

        // 第四步：检查value包含px的情况
        if (value != null && value.contains("px")) {
            for (String substring : PX_NO_TRANSLATE_SUBSTRINGS) {
                if (key.contains(substring)) {
                    return false;
                }
            }
        }

        // 如果以上条件都不满足，则需要翻译
        return true;
    }


}
