package com.bogdatech.utils;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static com.bogdatech.constants.TranslateConstants.*;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
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

    //白名单数据
    public static final Pattern TRANSLATABLE_KEY_PATTERN =
            Pattern.compile(".*(heading|description|content|title|label|product|faq|header|des|custom_html|text|slide|name|checkout).*");


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
        NO_TRANSLATE_KEYS.add("units");
        NO_TRANSLATE_KEYS.add("abbreviation");
        NO_TRANSLATE_KEYS.add("Abbreviation");
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
        JSON_NO_TRANSLATE_SUBSTRINGS.add("code");
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
        OLD_NO_TRANSLATE.add("rtl");
        OLD_NO_TRANSLATE.add("css:");
        OLD_NO_TRANSLATE.add("icon:");
        OLD_NO_TRANSLATE.add("swatch");
        OLD_NO_TRANSLATE.add("zindex");
        OLD_NO_TRANSLATE.add("wborder");
        OLD_NO_TRANSLATE.add("option:");
    }

    // URL前缀集合
    private static final Set<String> URL_PREFIXES = new HashSet<>();

    static {
        URL_PREFIXES.add("http://");
        URL_PREFIXES.add("https://");
        URL_PREFIXES.add("shopify://");
        URL_PREFIXES.add("gid://shopify");
    }

    // 正则表达式
    private static final Pattern PURE_NUMBER = Pattern.compile("^\\d+$"); // 纯数字
    public static final Pattern SUSPICIOUS_PATTERN = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])[A-Za-z0-9]{9,}$"); //如UXxSP8cSm，UgvyqJcxm。有大写字母和小写字母的组合。有大写字母，小写字母和数字的组合。
    public static final Pattern SUSPICIOUS2_PATTERN = Pattern.compile("^(?=.*[A-Z])[A-Za-z0-9]{10}$"); //10位大写和 数字组合
    public static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"); // UUID
    private static final Pattern DASH_PATTERN = Pattern.compile(
            "^[\\dA-Z+-.]+$" // 仅包含数字、全大写字母、标点符号（+、-、.()）
    );
    private static final Pattern DASH_WITH_HYPHEN = Pattern.compile(
            "^[\\dA-Z]*[-—][\\dA-Z]*$" // 包含至少一个-或—，前后为数字或全大写字母
    );
    private static final Pattern PHONE_NUMBER_PATTERN = Pattern.compile(
            "\\+\\d{1,3}(?:\\s?(?:\\(\\d+\\)|\\d+))?\\s?\\d[\\d\\s-]{3,13}\\d|" + // 国际号码
                    "\\+86\\s?1\\d{10}|" +                                                  // 中国大陆手机号码
                    "00\\d{1,3}\\s?1\\d{10}|" +                                             // 国际拨号前缀
                    "\\+\\d{8,15}"
    );//包含电话号码
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );//包含邮箱
    private static final Pattern BASE64_PATTERN = Pattern.compile("^(?=[A-Za-z0-9+/]*[A-Z])(?=[A-Za-z0-9+/]*[a-z])(?=[A-Za-z0-9+/]*[0-9])(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$"); // Base64编码
    private static final Pattern HASH_PATTERN = Pattern.compile("^[a-fA-F0-9]{64}$"); // 32位十六进制字符串
    // 长度限制常量
    private static final int HASH_PREFIX_MAX_LENGTH = 90;
    private static final int HASH_CONTAINS_MAX_LENGTH = 30;
    private static final int SLASH_CONTAINS_MAX_LENGTH = 20; // 可改为15
    private static final Pattern ICON_MATH_PATTERN = Pattern.compile(".*\\.icon_\\d+:.*"); // icon_X 类型
    public static final Pattern GENERAL_OR_SECTION_PATTERN = Pattern.compile("^(general|section)\\."); // general.开头的key

    /**
     * 判断给定的key是否需要翻译
     *
     * @param key   要检查的key
     * @param value 对应的value，可能为null
     * @return true表示需要翻译，false表示不需要翻译
     */
    public static boolean shouldTranslate(String key, String value) {
        if (value == null || value.trim().isEmpty()) {
            printTranslateReason("general.&section.  " + key + " is null or empty, value是： " + value);
            return false;
        }

        if(key.contains("captions")){
            printTranslateReason("general.&section. " + key + "包含captions, value是： " + value);
            return false;
        }

        //判断icon相关数据
        if (ICON_MATH_PATTERN.matcher(key).matches()) {
            printTranslateReason("general.&section. " + key + "icon相关数据, value是： " + value);
            return false;
        }

        //第一步： 检查是否为不翻译的key
        for (String substring : OLD_NO_TRANSLATE) {
            if (key.contains(substring)) {
                printTranslateReason("general.&section. " + key + "包含" + substring +"的字符, value是： " + value);
                return false;
            }
        }

        // 第二步：检查是否为明确不翻译的key
        for (String substring : NO_TRANSLATE_KEYS) {
            if (key.contains(substring)) {
                printTranslateReason("general.&section. " + key + "包含" + substring +"的字符, value是： " + value);
                return false;
            }
        }

        // 第三步：检查包含.json的key
        if (key.contains(".json")) {
            for (String substring : JSON_NO_TRANSLATE_SUBSTRINGS) {
                if (key.contains(substring)) {
                    printTranslateReason("general.&section. " + key + "包含.json且包含 " + substring +" 的字符, value是： " + value);
                    return false;
                }
            }
        }

        // 第十四步，如果key包含color 但 是html， 翻译
        if (key.contains("color") && !isHtml(value)) {
            printTranslateReason("general.&section. " + key + "包含color且不是html, value是： " + value);
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
            printTranslateReason(value + "包含px, key是： " + key);
            return false;
        }

        //第五步检查value是否为TRUE或FLASE
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            printTranslateReason(value + "是true或false, key是： " + key);
            return false;
        }

        //第六步，1.以#开头，且长度不超过90  2. 包含#，且长度不超过30
        if (value.startsWith("#") && value.length() <= HASH_PREFIX_MAX_LENGTH) {
            printTranslateReason(value + "以#开头，且长度不超过90, key是： " + key);
            return false;
        }

//        if (value.contains("#") && value.length() <= HASH_CONTAINS_MAX_LENGTH) {
//            printTranslateReason(value + "包含#，且长度不超过30, key是： " + key);
//            return false;
//        }

        // 第七步，纯数字
        if (PURE_NUMBER.matcher(value).matches()) {
            printTranslateReason(value + "是纯数字, key是： " + key);
            return false;
        }

        // 第八步，包含http://、https://或shopify://
        for (String prefix : URL_PREFIXES) {
            if (value.contains(prefix)) {
                printTranslateReason(value + "包含" + prefix + ", key是： " + key );
                return false;
            }
        }

        // 第九步，包含/，且长度不超过20
        if (value.contains("/") && value.length() <= SLASH_CONTAINS_MAX_LENGTH) {
            printTranslateReason(value + "包含/，且长度不超过20, key是： " + key);
            return false;
        }

        // 第十步，包含-或—，检查特定模式
        if (value.contains("-") || value.contains("—")) {
            if (DASH_PATTERN.matcher(value).matches()) {
                printTranslateReason(value + "包含-或—，且符合特定模式, key是： " + key);
                return false;

            }
            // 仅包含数字、全大写字母、标点符号，且有-或—
            if (DASH_WITH_HYPHEN.matcher(value).matches()) {
                printTranslateReason(value + "仅包含数字、全大写字母、标点符号，且有-或—, key是： " + key);
                return false;
            }
        }

        // 第十一步，包含<svg>
        if (value.contains("<svg>")) {
            printTranslateReason(value + "包含<svg>, key是： " + key);
            return false;
        }

        // 第十二步，包含电话号码
        if (PHONE_NUMBER_PATTERN.matcher(value).matches()) {
            printTranslateReason(value + "包含电话号码, key是： " + key);
            return false;
        }

        //第十三步，包含邮箱
        if (EMAIL_PATTERN.matcher(value).matches()) {
            printTranslateReason(value + "包含邮箱, key是： " + key);
            return false;
        }

        //如果值为纯数字,小数或负数的话，不翻译
        if (isNumber(value)) {
            printTranslateReason(value + "是纯数字,小数或负数, key是： " + key);
            return false;
        }

        //如果是UUID类别的数据。不翻译
        if (UUID_PATTERN.matcher(value).matches()) {
            printTranslateReason(value + "是UUID类别的数据, key是： " + key);
            return false;
        }

        //如果是base64编码的数据，不翻译
        if (BASE64_PATTERN.matcher(value).matches()) {
            printTranslateReason(value + "是base64编码的数据, key是： " + key);
            return false;
        }

        //如果是32位十六进制字符串值，不翻译
        if (HASH_PATTERN.matcher(value).matches()) {
            printTranslateReason(value + "是32位十六进制字符串值, key是： " + key);
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

    /**
     * 打印被白名单和黑名单命中的理由
     * */
    public static void printTranslateReason(String reason) {
        appInsights.trackTrace("命中的理由： " + reason);
    }
}
