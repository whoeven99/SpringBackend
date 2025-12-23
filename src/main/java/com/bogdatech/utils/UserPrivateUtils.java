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
}
