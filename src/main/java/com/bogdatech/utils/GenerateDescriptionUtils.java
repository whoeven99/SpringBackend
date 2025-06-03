package com.bogdatech.utils;

import com.alibaba.fastjson.JSONObject;

public class GenerateDescriptionUtils {
    /**
     * 生成产品描述的提示词
     * @param productTitle 产品标题
     * @param language 语言
     * @return 提示词
     * */
    public static String generatePrompt(String productTitle, String language){
        return "I will give you a product title: " +  productTitle + ". Help me generate a " + language + " product description. Just output the product description, no explanation text is required. The product description format requirements are as follows:";
    }

    /**
     * 解析shopifyData数据
     * */
    public static String parseShopifyData(String shopifyData){
        if (shopifyData == null || shopifyData.isEmpty()){
            return null;
        }
        JSONObject obj = JSONObject.parseObject(shopifyData);
        String title = obj.getJSONObject("product").getString("title");

        return title;
    }
}
