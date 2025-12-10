package com.bogdatech.requestBody;

import com.bogdatech.entity.DO.TranslateResourceDTO;

public class ShopifyRequestBody {

    // TODO move to ShopifyRequestUtils
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

    // TODO move to ShopifyRequestUtils
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
                "      trialDays\n" +
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
     * 根据用户单独购买积分的gid，获取订单信息
     */
    public static String getSingleQuery(String singleId) {
        return "query MyQuery {\n" +
                "  node(id: \"" + singleId + "\") {\n" +
                "    ... on AppPurchaseOneTime {\n" +
                "      id\n" +
                "      name\n" +
                "      createdAt\n" +
                "      price {\n" +
                "        amount\n" +
                "        currencyCode\n" +
                "      }\n" +
                "      status\n" +
                "      test\n" +
                "    }\n" +
                "  }\n" +
                "}";
    }

    /**
     * 根据查询语句获取用户所有语言代码
     */
    public static String getLanguagesQuery() {
        return "query MyQuery {\n" +
                "  shopLocales {\n" +
                "    locale\n" +
                "    name\n" +
                "    primary\n" +
                "    published\n" +
                "  }\n" +
                "}";
    }

    /**
     * 根据产品id获取对应信息
     */
    public static String getProductDataQuery(String productId) {
        return "{\n" +
                "  product(id: \"" + productId + "\") {\n" +
                "    descriptionHtml\n" +
                "    id\n" +
                "    media(first: 1) {\n" +
                "      edges {\n" +
                "        node {\n" +
                "          ... on MediaImage {\n" +
                "            image {\n" +
                "              url\n" +
                "              altText\n" +
                "            }\n" +
                "          }\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "    productType\n" +
                "    title\n" +
                "  }\n" +
                "}";
    }

    /**
     * 获取用户商店开启的语言和未开启的语言
     */
    public static String getShopLanguageQuery() {
        return """
                query MyQuery {
                   shopLocales(published: false) {
                         locale
                         name
                         primary
                         published
                         }
                }
                """;
    }
}
