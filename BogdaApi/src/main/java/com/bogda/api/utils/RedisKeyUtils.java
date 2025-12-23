package com.bogda.api.utils;

public class RedisKeyUtils {
    // 模板字符串，所有 key 统一放在这里
    // redis 缓存模板字符串
    public static final String TRANSLATE_CACHE_KEY_TEMPLATE = "tc:{targetCode}:{source}";
    public static final Long DAY_14 = 1209600L;
    public static final String DATA_REPORT_KEY_TEMPLATE = "dr:{shopName}:{language}:{yyyyMMdd}";
    public static final String DATA_REPORT_KEY_TEMPLATE_KEYS = "drs:{shopName}:keys";
    public static final Long DAY_15 = 2592000L;
    public static final Long DAY_1 = 86400L;
    // 对clientId去重 set
    public static final String CLIENT_ID_SET = "ci:{shopName}:{language}:{yyyyMMdd}:{eventName}";
    public static final String STOPPED_FLAG = "tsk:stp:{shopName}";
}
