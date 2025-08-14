package com.bogdatech.utils;

public class UserPrivateUtils {

    /**
     * 处理用户名称和模型，生成为apiKey数据
     * */
    public static String getApiKey(String userName, Integer apiName) {
        //修改userName，将.号处理掉
        userName = userName.replace(".", "");
        return userName + "-" + apiName;
    }

    /**
     * 处理key数据，中间以*号返回
     * */
    public static String maskString(String input) {
        if (input == null || input.length() <= 8) {
            // 长度不足时直接返回原字符串
            return input;
        }
        String prefix = input.substring(0, 4);
        String suffix = input.substring(input.length() - 4);
        // 中间用星号填充
        String maskedMiddle = "*".repeat(input.length() - 8);
        return prefix + maskedMiddle + suffix;
    }
}
