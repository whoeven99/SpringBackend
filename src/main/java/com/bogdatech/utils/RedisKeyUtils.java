package com.bogdatech.utils;

public class RedisKeyUtils {
    // 模板字符串，所有 key 统一放在这里
    private static final String TRANSLATE_PROGRESS_KEY_TEMPLATE = "tr:{shopName}:{targetCode}";
    //redis进度条 total
    public static final String PROGRESS_TOTAL = "total";
    //redis进度条 done
    public static final String PROGRESS_DONE = "done";
    //redis 缓存模板字符串
    private static final String TRANSLATE_CACHE_KEY_TEMPLATE = "tc:{targetCode}:{source}";
    public static final Long DAY_14 = 1209600L;

    /**
     * 生成翻译进度的 Redis key
     */
    public static String generateProcessKey(String shopName, String targetCode) {
        if (shopName == null || targetCode == null) {
            return null;
        }
        return TRANSLATE_PROGRESS_KEY_TEMPLATE
                .replace("{shopName}", shopName)
                .replace("{targetCode}", targetCode);
    }

    /**
     * 生成翻译缓存的 Redis key
     */
    public static String generateCacheKey(String targetCode, String digest) {
        if (targetCode == null || digest == null) {
            return null;
        }
        return TRANSLATE_CACHE_KEY_TEMPLATE.replace("{targetCode}", targetCode)
                .replace("{source}", digest);
    }
}
