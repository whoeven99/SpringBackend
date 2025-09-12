package com.bogdatech.utils;

public class RedisKeyUtils {
    /**
     * 根据shopName，target，field生成对应的key
     * */
    public class RedisKeyUtil {

        // 模板字符串，所有 key 统一放在这里
        private static final String TRANSLATE_PROGRESS_KEY_TEMPLATE = "tr:{shopName}:{targetCode}";
        //redis进度条 total
        public static final String PROGRESS_TOTAL = "total";
        //redis进度条 done
        public static final String PROGRESS_DONE = "done";

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
    }
}
