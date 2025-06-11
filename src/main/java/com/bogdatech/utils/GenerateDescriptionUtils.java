package com.bogdatech.utils;

import com.alibaba.fastjson.JSONObject;
import net.sf.jsqlparser.statement.select.Top;

import static com.bogdatech.requestBody.ShopifyRequestBody.getCollectionsQueryById;
import static com.bogdatech.requestBody.ShopifyRequestBody.getProductsQueryById;

public class GenerateDescriptionUtils {
    /**
     * 生成产品描述的提示词
     *
     * @param productTitle 产品标题
     * @param language     语言
     * @return 提示词
     */
    public static String generatePrompt(String productTitle, String language) {
        return "I will give you a product title: " + productTitle + ". Help me generate a " + language + " product description. Just output the product description, no explanation text is required. The product description format requirements are as follows:";
    }

    /**
     * 生成seo的提示词
     *
     * @param productTitle 产品标题
     * @param language     语言
     * @return 提示词
     */
    public static String generateSeoPrompt(String productTitle, String language) {
        return "Generate an SEO-optimized product description (Limit: Maximum 320 characters) in " + language + " for the following product: " + productTitle + ".Format requirements are as follows: ";
    }

    /**
     * 解析shopifyData数据
     */
    public static String parseShopifyData(String shopifyData, String type) {
        if (shopifyData == null || shopifyData.isEmpty()) {
            return null;
        }
        JSONObject obj = JSONObject.parseObject(shopifyData);
        String title = null;
        if ("product".equals(type)) {
            title = obj.getJSONObject("product").getString("title");
        } else {
            title = obj.getJSONObject("collection").getString("title");
        }

        return title;
    }
}
