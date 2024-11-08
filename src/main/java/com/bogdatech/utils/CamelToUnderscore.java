package com.bogdatech.utils;

public class CamelToUnderscore {
    // 将驼峰命名法转换为下划线命名法
    public static Object camelToUnderscore(String input) {
        return input.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
