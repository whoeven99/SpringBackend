package com.bogdatech.utils;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static com.bogdatech.constants.TranslateConstants.*;
import static com.bogdatech.utils.JsoupUtils.isHtml;
import static com.bogdatech.utils.StringUtils.isNumber;

public class JudgeTranslateUtils {

    //主题模块
    public static final Set<String> TRANSLATABLE_RESOURCE_TYPES = Set.of(
            ONLINE_STORE_THEME,
            ONLINE_STORE_THEME_APP_EMBED,
            ONLINE_STORE_THEME_JSON_TEMPLATE,
            ONLINE_STORE_THEME_SECTION_GROUP,
            ONLINE_STORE_THEME_SETTINGS_CATEGORY,
            ONLINE_STORE_THEME_SETTINGS_DATA_SECTIONS,
            ONLINE_STORE_THEME_LOCALE_CONTENT
    );

    public static final Pattern TRANSLATABLE_KEY_PATTERN =
            Pattern.compile(".*(heading|description|content|title|label|product|faq|header|des|custom_html|text|slide|name).*");


    // 明确不翻译的key集合
    private static final Set<String> NO_TRANSLATE_KEYS = new HashSet<>();

    static {
        NO_TRANSLATE_KEYS.add("general.rtl_languages");
        NO_TRANSLATE_KEYS.add("general.custom_css");
        NO_TRANSLATE_KEYS.add("shopify.checkout.order_summary.shipping_pending_value");
        NO_TRANSLATE_KEYS.add("customer_accounts.order_details.no_data_provided");
        NO_TRANSLATE_KEYS.add("checkout.contact");
        NO_TRANSLATE_KEYS.add("_font");
        NO_TRANSLATE_KEYS.add("spacing");
        NO_TRANSLATE_KEYS.add("items_resp");
        NO_TRANSLATE_KEYS.add("animations_type");
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
        JSON_NO_TRANSLATE_SUBSTRINGS.add("grid_");
        JSON_NO_TRANSLATE_SUBSTRINGS.add("variant_");
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
        PX_NO_TRANSLATE_SUBSTRINGS.add("fs");
        PX_NO_TRANSLATE_SUBSTRINGS.add("lh");
        PX_NO_TRANSLATE_SUBSTRINGS.add("mr");
    }

    //key中包含以下字符不翻译（原先的逻辑）
    private static final Set<String> OLD_NO_TRANSLATE = new HashSet<>();

    static {
        OLD_NO_TRANSLATE.add("metafield:");
        OLD_NO_TRANSLATE.add("formId:");
        OLD_NO_TRANSLATE.add("phone_text");
        OLD_NO_TRANSLATE.add("email_text");
        OLD_NO_TRANSLATE.add("carousel_easing");
        OLD_NO_TRANSLATE.add("_link");
        OLD_NO_TRANSLATE.add("general.rtl");
        OLD_NO_TRANSLATE.add("css:");
        OLD_NO_TRANSLATE.add("icon:");
    }

    // URL前缀集合
    private static final Set<String> URL_PREFIXES = new HashSet<>();

    static {
        URL_PREFIXES.add("http://");
        URL_PREFIXES.add("https://");
        URL_PREFIXES.add("shopify://");
    }

    // 正则表达式
    private static final Pattern PURE_NUMBER = Pattern.compile("^\\d+$"); // 纯数字
    public static final Pattern SUSPICIOUS_PATTERN = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])[A-Za-z0-9]{9,}$"); //如UXxSP8cSm，UgvyqJcxm。有大写字母和小写字母的组合。有大写字母，小写字母和数字的组合。
    private static final Pattern DASH_PATTERN = Pattern.compile(
            "^[\\dA-Z+-.]+$" // 仅包含数字、全大写字母、标点符号（+、-、.()）
    );
    private static final Pattern DASH_WITH_HYPHEN = Pattern.compile(
            "^[\\dA-Z]*[-—][\\dA-Z]*$" // 包含至少一个-或—，前后为数字或全大写字母
    );
    private static final Pattern PHONE_NUMBER_PATTERN = Pattern.compile(
            "^[\\d\\s]*(\\(\\+\\d+\\))?[\\d\\s]*$"
    );//包含电话号码
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );//包含邮箱

    // 长度限制常量
    private static final int HASH_PREFIX_MAX_LENGTH = 90;
    private static final int HASH_CONTAINS_MAX_LENGTH = 30;
    private static final int SLASH_CONTAINS_MAX_LENGTH = 20; // 可改为15


    /**
     * 判断给定的key是否需要翻译
     *
     * @param key   要检查的key
     * @param value 对应的value，可能为null
     * @return true表示需要翻译，false表示不需要翻译
     */
    public static boolean shouldTranslate(String key, String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }

        if(key.contains("captions")){
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

        // 第十四步，如果key包含color 但 是html， 翻译
        if (key.contains("color") && !isHtml(value)) {
            return false;
        }

        // 如果以上条件都不满足，则需要翻译
        return true;
    }


    /**
     * 通用不翻译的数据类型判断
     *
     * @param key   要检查的key
     * @param value 对应的value，可能为null
     * @return true表示需要翻译，false表示不需要翻译
     */
    public static boolean generalTranslate(String key, String value) {
        if (isHtml(value)){
            return true;
        }
        // 第四步：检查value包含px的情况
        if (value.contains("px")) {
            return false;
        }

        //第五步检查value是否为TRUE或FLASE
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return false;
        }

        //第六步，1.以#开头，且长度不超过90  2. 包含#，且长度不超过30
        if (value.startsWith("#") && value.length() <= HASH_PREFIX_MAX_LENGTH) {
            return false;
        }

        if (value.contains("#") && value.length() <= HASH_CONTAINS_MAX_LENGTH) {
            return false;
        }

        // 第七步，纯数字
        if (PURE_NUMBER.matcher(value).matches()) {
            return false;
        }

        // 第八步，包含http://、https://或shopify://
        for (String prefix : URL_PREFIXES) {
            if (value.contains(prefix)) {
                return false;
            }
        }

        // 第九步，包含/，且长度不超过20
        if (value.contains("/") && value.length() <= SLASH_CONTAINS_MAX_LENGTH) {
            return false;
        }

        // 第十步，包含-或—，检查特定模式
        if (value.contains("-") || value.contains("—")) {
            if (DASH_PATTERN.matcher(value).matches()) {
                return false;

            }
            // 仅包含数字、全大写字母、标点符号，且有-或—
            if (DASH_WITH_HYPHEN.matcher(value).matches()) {
                return false;
            }
        }

        // 第十一步，包含<svg>
        if (value.contains("<svg>")) {
            return false;
        }

        // 第十二步，包含电话号码
        if (PHONE_NUMBER_PATTERN.matcher(value).matches()) {
            return false;
        }

        //第十三步，包含邮箱
        if (EMAIL_PATTERN.matcher(value).matches()) {
            return false;
        }

        //如果值为纯数字,小数或负数的话，不翻译
        if (isNumber(value)) {
            return false;
        }

        // 如果以上条件都不满足，则需要翻译
        return true;
    }

    /**
     * 元字段对应的数据不翻译，left，right，top，bottom
     * @param value 对应的value，可能为null
     * @return true表示需要翻译，false表示不需要翻译
     */
    public static boolean metaTranslate(String value) {
        return !value.equals("left") && !value.equals("right") && !value.equals("top") && !value.equals("bottom");
    }
}
