package com.bogdatech.utils;

public class CamelToUnderscore {
    public static Object camelToUnderscore(String input) {
        return input.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
