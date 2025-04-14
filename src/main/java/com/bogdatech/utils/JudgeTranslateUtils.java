package com.bogdatech.utils;

public class JudgeTranslateUtils {

    //初始化 静态变量
    static {

    }
    /**
     * @param value 需要翻译的值
     * @param key 需要翻译的key
     * **/
    public static boolean isTranslate(String value, String key){
        //TODO 判断是否需要翻译
        if (key.contains("metafield:") || key.contains("color")
                ||key.contains("formId:") ||key.contains("phone_text") ||key.contains("email_text")
                ||key.contains("carousel_easing") || key.contains("_link")|| key.contains("general.rtl") || key.contains("css:")
                || key.contains("icon:") || "handle".equals(key) ) {
            return false;
        }




        return true;
    }
}
