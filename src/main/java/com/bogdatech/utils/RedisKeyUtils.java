package com.bogdatech.utils;

public class RedisKeyUtils {
    // 模板字符串，所有 key 统一放在这里
    private static final String TRANSLATE_PROGRESS_KEY_TEMPLATE = "tr:{shopName}:{targetCode}";
    // redis进度条 total
    public static final String PROGRESS_TOTAL = "total";
    // redis进度条 done
    public static final String PROGRESS_DONE = "done";
    // redis 缓存模板字符串
    private static final String TRANSLATE_CACHE_KEY_TEMPLATE = "tc:{targetCode}:{source}";
    public static final Long DAY_14 = 1209600L;
    public static final String DATA_REPORT_KEY_TEMPLATE = "dr:{shopName}:{language}:{yyyyMMdd}";
    public static final String DATA_REPORT_KEY_TEMPLATE_KEYS = "drs:{shopName}:keys";
    public static final Long DAY_15 = 2592000L;
    public static final Long DAY_1 = 86400L;
    // 对clientId去重 set
    public static final String CLIENT_ID_SET = "ci:{shopName}:{language}:{yyyyMMdd}:{eventName}";
    // 翻译商店锁
    public static final String TRANSLATE_LOCK = "tl:{shopName}";
    public static final String TRANSLATE_LOCK_TRUE = "1";
    // redis 存用户查询语言状态（翻译状态）
    public static final String TRANSLATE_USER_STATUS = "us:{shopName}:{sourceCode}:{targetCode}";


    /**
     * 生成语言状态的 key
     * */
    public static String generateTranslateUserStatusKey(String shopName, String sourceCode, String targetCode) {
        if (shopName == null || sourceCode == null || targetCode == null) {
            return null;
        }
        return TRANSLATE_USER_STATUS.replace("{shopName}", shopName)
                .replace("{sourceCode}", sourceCode)
                .replace("{targetCode}", targetCode);
    }

    /**
     * 生成翻译锁的 key
     * */
    public static String generateTranslateLockKey(String shopName) {
        if (shopName == null) {
            return null;
        }
        return TRANSLATE_LOCK.replace("{shopName}", shopName);
    }

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

    /**
     * 生成数据上报的key
     * */
    public static String generateDataReportKey(String shopName, String language, String time) {
        if (shopName == null || language == null || time == null) {
            return null;
        }
        return DATA_REPORT_KEY_TEMPLATE.replace("{shopName}", shopName)
                .replace("{language}", language)
                .replace("{yyyyMMdd}", time);
    }

    /**
     * 生成由于clientId去重的key
     * */
    public static String generateClientIdSetKey(String shopName, String language, String time, String eventName) {
        if (shopName == null || language == null || time == null) {
            return null;
        }
        return CLIENT_ID_SET.replace("{shopName}", shopName)
                .replace("{language}", language)
                .replace("{yyyyMMdd}", time)
                .replace("{eventName}", eventName);
    }

    /**
     * 生成数据上报的keys
     * */
    public static String generateDataReportKeyKeys(String shopName) {
        if (shopName == null) {
            return null;
        }
        return DATA_REPORT_KEY_TEMPLATE_KEYS.replace("{shopName}", shopName);
    }

}
