package com.bogdatech.utils;

public class WhiteListUtils {
    // 仅返回boolean
    public static Boolean checkWhiteList(String shopName){
        return "5bf8b3.myshopify.com".equals(shopName) || "c5ba7c-7c.myshopify.com".equals(shopName) || "digitevil.myshopify.com".equals(shopName);
    }

    public static boolean singleTranslateWhiteList(String shopName) {
        return "ciwishop.myshopify.com".equals(shopName);
    }

    public static boolean clickTranslateWhiteList(String shopName) {
//        return "ciwishop.myshopify.com".equals(shopName);
        return false;
    }

    public static boolean autoTranslateWhiteList(String shopName) {
//        return "ciwishop.myshopify.com".equals(shopName);
        return false;
    }
}
