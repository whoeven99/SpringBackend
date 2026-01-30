package com.bogda.common.utils;

import com.bogda.common.contants.TranslateConstants;
import com.bogda.common.enums.RejectRuleEnum;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import static com.bogda.common.utils.JsoupUtils.isHtml;

public class JudgeTranslateUtils {

    //主题模块
    public static final Set<String> TRANSLATABLE_RESOURCE_TYPES = Set.of(
            TranslateConstants.ONLINE_STORE_THEME,
            TranslateConstants.ONLINE_STORE_THEME_APP_EMBED,
            TranslateConstants.ONLINE_STORE_THEME_JSON_TEMPLATE,
            TranslateConstants.ONLINE_STORE_THEME_SECTION_GROUP,
            TranslateConstants.ONLINE_STORE_THEME_SETTINGS_CATEGORY,
            TranslateConstants.ONLINE_STORE_THEME_SETTINGS_DATA_SECTIONS,
            TranslateConstants.ONLINE_STORE_THEME_LOCALE_CONTENT
    );

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
//        JSON_NO_TRANSLATE_SUBSTRINGS.add("css");
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
//        OLD_NO_TRANSLATE.add("_link");
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

    // 白名单单词集合
    private static final Set<String> WHITELIST_WORDS = new HashSet<>();
    static {
        WHITELIST_WORDS.add("heading");
        WHITELIST_WORDS.add("text");
        WHITELIST_WORDS.add("description");
        WHITELIST_WORDS.add("content");
        WHITELIST_WORDS.add("title");
        WHITELIST_WORDS.add("label");
        WHITELIST_WORDS.add("product");
        WHITELIST_WORDS.add("faq");
        WHITELIST_WORDS.add("header");
        WHITELIST_WORDS.add("des");
        WHITELIST_WORDS.add("custom_html");
        WHITELIST_WORDS.add("slide");
        WHITELIST_WORDS.add("name");
        WHITELIST_WORDS.add("checkout");
    }

    // 正则表达式
    public static final Pattern SUSPICIOUS_PATTERN = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])[A-Za-z0-9]{9,}$"); //如UXxSP8cSm，UgvyqJcxm。有大写字母和小写字母的组合。有大写字母，小写字母和数字的组合。
    public static final Pattern SUSPICIOUS2_PATTERN = Pattern.compile("^(?=.*[A-Z])[A-Za-z0-9]{10}$"); //10位大写和 数字组合
    public static final Pattern BASE64_PATTERN = Pattern.compile("^(?=[A-Za-z0-9+/]*[A-Z])(?=[A-Za-z0-9+/]*[a-z])(?=[A-Za-z0-9+/]*[0-9])(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$"); // Base64编码

    // 长度限制常量
    private static final int HASH_PREFIX_MAX_LENGTH = 90;
    private static final int SLASH_CONTAINS_MAX_LENGTH = 20; // 可改为15
    private static final Pattern ICON_MATH_PATTERN = Pattern.compile(".*\\.icon_\\d+:.*"); // icon_X 类型
    public static final Pattern GENERAL_OR_SECTION_PATTERN = Pattern.compile("^(general|section)\\."); // general.开头的key

    /**
     * theme模块判断给定的key是否需要翻译
     *
     * @param key   要检查的key
     * @param value 对应的value，可能为null
     * @return true表示需要翻译，false表示不需要翻译
     */
    public static boolean shouldTranslate(String key, String value) {
        if (value == null || value.trim().isEmpty()) {
            printTranslateReason("key: " + key + " is null or empty, value是： " + value);
            return false;
        }

        if (value.startsWith("=")){
            printTranslateReason("value: " + value + " 是以=开头, key是： " + key);
            return false;
        }

        if(key.contains("captions")){
            printTranslateReason("general.&section. " + key + "包含captions, value是： " + value);
            return false;
        }

        // 包含/，且长度不超过20
        if (value.contains("/") && value.length() <= SLASH_CONTAINS_MAX_LENGTH) {
            printTranslateReason(value + "包含/，且长度不超过20, key是： " + key);
            return false;
        }

        //如果是base64编码的数据，不翻译
        if (BASE64_PATTERN.matcher(value).matches()) {
            printTranslateReason(value + "是base64编码的数据, key是： " + key);
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

    private static final RejectRuleEnum[] ALL_RULES = RejectRuleEnum.values();

    /**
     * 通用不翻译的数据类型判断
     *
     * @param key   要检查的key
     * @param value 对应的value，可能为null
     * @return true表示需要翻译，false表示不需要翻译
     */
    public static boolean translationRuleJudgment(String key, String value) {

        if (value == null || value.isBlank()) {
            return false;
        }

        if ("value".equals(key) && JsonUtils.isJson(value)){
            return true;
        }

        if (JsoupUtils.isHtml(value)) {
            return true;
        }

        if (value.contains("px")) {
            printTranslateReason(value + "包含px, key是： " + key);
            return false;
        }

        // 检查value是否为TRUE或FLASE
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            printTranslateReason(value + "是true或false, key是： " + key);
            return false;
        }

        // 1.以#开头，且长度不超过90
        if (value.startsWith("#") && value.length() <= HASH_PREFIX_MAX_LENGTH) {
            printTranslateReason(value + "以#开头，且长度不超过90, key是： " + key);
            return false;
        }

        // 包含http://、https://或shopify://
        for (String prefix : URL_PREFIXES) {
            if (value.startsWith(prefix)) {
                printTranslateReason(value + "包含" + prefix + ", key是： " + key );
                return false;
            }
        }

        // enum 规则驱动
        for (RejectRuleEnum rule : ALL_RULES) {
            if (rule.matches(value)) {
                printTranslateReason(value + " 命中规则：" + rule.getReason() + ", key是：" + key);
                return false;
            }
        }

        return true;
    }

    /**
     * 元字段对应的数据不翻译，left，right，top，bottom
     * @param value 对应的value，可能为null
     * @return true表示需要翻译，false表示不需要翻译
     */
    public static boolean metaTranslate(String value) {
        return !"left".equals(value) && !"right".equals(value) && !"top".equals(value) && !"bottom".equals(value);
    }

    /**
     * 打印被白名单和黑名单命中的理由
     * */
    public static void printTranslateReason(String reason) {
        AppInsightsUtils.trackTrace("命中的理由： " + reason);
    }

    /**
     * 在主题翻译黑名单前添加一个白名单规则，命中后直接翻译
     * */
    public static boolean whiteListTranslate(String key) {
        String prefix = key.split(":")[0];
        for (String text: WHITELIST_WORDS
             ) {
            if (prefix.endsWith(text)) {
                AppInsightsUtils.trackTrace("以 " + text + " 结尾");
                return true;
            }
        }
        return false;
    }

}
