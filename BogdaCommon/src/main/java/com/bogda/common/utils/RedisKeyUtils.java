package com.bogda.common.utils;

public class RedisKeyUtils {
    // 模板字符串，所有 key 统一放在这里
    // redis 缓存模板字符串
    public static final String TRANSLATE_CACHE_KEY_TEMPLATE = "tc:{targetCode}:{source}";
    public static final Long DAY_14 = 1209600L;
    public static final Long DAY_1 = 86400L;
}
