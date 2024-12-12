package com.bogdatech.utils;


public class CaseSensitiveUtils {

    //区分大小写
    public static boolean isCaseSensitiveEqual(String str1, String str2) {
        return str1 != null && str1.equals(str2);
    }

    //不区分大小写
    public static boolean isCaseInsensitiveEqual(String str1, String str2) {
        return str1 != null && str1.equalsIgnoreCase(str2);
    }
}
