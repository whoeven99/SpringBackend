package com.bogda.api.requestBody;

public class ShopifyRequestBody {
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

}
