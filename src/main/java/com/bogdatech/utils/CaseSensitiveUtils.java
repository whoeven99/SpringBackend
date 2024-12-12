package com.bogdatech.utils;


public class CaseSensitiveUtils {

    //区分大小写
    public static boolean containsValue(String text, String value) {
        if (text == null || value == null) {
            return false;
        }
        return text.contains(value);
    }

    //不区分大小写
    public static boolean containsValueIgnoreCase(String text, String value) {
        if (text == null || value == null) {
            return false;
        }
        return text.toLowerCase().contains(value.toLowerCase());
    }
}
