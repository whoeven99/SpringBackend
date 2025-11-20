package com.bogdatech.utils;

import java.util.HashMap;
import java.util.Map;

public class ConfigUtils {
    public static Map<String, String> config = new HashMap<>();

    public static String getConfig(String key) {
        // 优先读取azure config
        String value = System.getenv(key);
        if (value != null) {
            return value;
        }

        // 从 application.{}.properties 中读取
        return config.get(key);
    }

    public static boolean isLocalEnv() {
        String env = System.getenv("ApplicationEnv");
        return env == null || "local".equals(env);
    }
}