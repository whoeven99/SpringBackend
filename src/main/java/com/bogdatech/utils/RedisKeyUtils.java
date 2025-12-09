package com.bogdatech.utils;

public class RedisKeyUtils {
    // 模板字符串，所有 key 统一放在这里
    // 翻译进度条key
    private static final String TRANSLATE_PROGRESS_KEY_TEMPLATE = "tr:{shopName}:{targetCode}";
    // redis进度条 total
    public static final String PROGRESS_TOTAL = "total";
    // redis进度条 done
    public static final String PROGRESS_DONE = "done";
    // redis 缓存模板字符串
    public static final String TRANSLATE_CACHE_KEY_TEMPLATE = "tc:{targetCode}:{source}";
    public static final Long DAY_14 = 1209600L;
    public static final String DATA_REPORT_KEY_TEMPLATE = "dr:{shopName}:{language}:{yyyyMMdd}";
    public static final String DATA_REPORT_KEY_TEMPLATE_KEYS = "drs:{shopName}:keys";
    public static final Long DAY_15 = 2592000L;
    public static final Long DAY_1 = 86400L;
    // 对clientId去重 set
    public static final String CLIENT_ID_SET = "ci:{shopName}:{language}:{yyyyMMdd}:{eventName}";
    // redis 存用户查询语言状态（翻译状态）
    public static final String TRANSLATE_USER_STATUS = "us:{shopName}:{sourceCode}:{targetCode}";
    public static final String STOPPED_FLAG = "tsk:stp:{shopName}";

    public static String generateProcessKey(String shopName, String targetCode) {
        if (shopName == null || targetCode == null) {
            return null;
        }
        return TRANSLATE_PROGRESS_KEY_TEMPLATE
                .replace("{shopName}", shopName)
                .replace("{targetCode}", targetCode);
    }
}
