package com.bogdatech.requestBody;

import com.bogdatech.entity.DO.TranslateResourceDTO;

public class ShopifyRequestBody {

    public String getFirstQuery(TranslateResourceDTO translateResourceDTO) {
        return "{\n" +
                "  translatableResources(resourceType: " + translateResourceDTO.getResourceType() + ", first: " + translateResourceDTO.getFirst() + ") {\n" +
                "    nodes {\n" +
                "      resourceId\n" +
                "      translations(locale: \"" + translateResourceDTO.getTarget() + "\") {\n" +
                "        locale\n" +
                "        value\n" +
                "        key\n" +
                "        outdated\n" +
                "      }\n" +
                "      translatableContent {\n" +
                "        type\n" +
                "        locale\n" +
                "        key\n" +
                "        value\n" +
                "        digest\n" +
                "      }\n" +
                "    }\n" +
                "    pageInfo {\n" +
                "      endCursor\n" +
                "      hasNextPage\n" +
                "    }\n" +
                "  }\n" +
                "}";
    }

    public String getAfterQuery(TranslateResourceDTO translateResourceDTO) {
        return "{\n" +
                "  translatableResources(resourceType: " + translateResourceDTO.getResourceType() + ", first: " + translateResourceDTO.getFirst() + ", after: " + "\"" + translateResourceDTO.getAfter() + "\"" + ") {\n" +
                "    nodes {\n" +
                "      resourceId" +
                "      translations(locale: \"" + translateResourceDTO.getTarget() + "\") {\n" +
                "        locale\n" +
                "        value\n" +
                "        key\n" +
                "        outdated\n" +
                "      }\n" +
                "      translatableContent {\n" +
                "        digest\n" +
                "        key\n" +
                "        type\n" +
                "        locale\n" +
                "        value\n" +
                "      }\n" +
                "    }\n" +
                "    pageInfo {\n" +
                "      endCursor\n" +
                "      hasNextPage\n" +
                "    }\n" +
                "  }\n" +
                "}";
    }

    public String registerTransactionQuery() {
        return "mutation translationsRegister($resourceId: ID!, $translations: [TranslationInput!]!) {\n" +
                "  translationsRegister(resourceId: $resourceId, translations: $translations) {\n" +
                "    userErrors {\n" +
                "      message\n" +
                "      field\n" +
                "    }\n" +
                "    translations {\n" +
                "      key\n" +
                "      value\n" +
                "    }\n" +
                "  }\n" +
                "}";
    }

    public String test(){
        return "{\n" +
                "\tshop {\n" +
                "\t\tname\n" +
                "\t}\n" +
                "}";
    }

    //根据用户付费订单id获取订单信息
    public static String getSubscriptionQuery(String subscriptionId) {
        return "query GetSubscriptionDetails {\n" +
                "  node(id: \"" + subscriptionId + "\") {\n" +
                "    ... on AppSubscription {\n" +
                "      id\n" +
                "      name\n" +
                "      status\n" +
                "      createdAt\n" +
                "      currentPeriodEnd\n" +
                "      lineItems {\n" +
                "        plan {\n" +
                "          pricingDetails {\n" +
                "            __typename\n" +
                "            ... on AppRecurringPricing {\n" +
                "              price {\n" +
                "                amount\n" +
                "                currencyCode\n" +
                "              }\n" +
                "            }\n" +
                "          }\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";
    }

    /**
     * 根据产品id获取对应的数据,目前简单的写就是获取title的值
     * */
    public static String getProductsQueryById(String productId) {
        return "query MyQuery {\n" +
                "  product(id: \"" + productId + "\") {\n" +
                "    title\n" +
                "  }\n" +
                "}";
    }
    /**
     * 根据集合id获取对应的数据,目前简单的写就是获取title的值
     * */
    public static String getCollectionsQueryById(String collectionId) {
        return "query {\n" +
                "  collection(id: \"" + collectionId + "\") {\n" +
                "    title\n" +
                "  }\n" +
                "}";
    }
}
