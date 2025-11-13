package com.bogdatech.utils;

public class ShopifyRequestUtils {
    public static String getQuery(String resourceType, String first, String target) {
        return "{\n" +
                "  translatableResources(resourceType: " + resourceType + ", first: " + first + ") {\n" +
                query.replace("%target%", target);
    }

    public static String getQuery(String resourceType, String first, String target, String after) {
        return "{\n" +
                "  translatableResources(resourceType: " + resourceType + ", first: " + first + ", after: " + "\"" + after + "\"" + ") {\n" +
                query.replace("%target%", target);
    }

    public static String query =
            "    nodes {\n" +
            "      resourceId\n" +
            "      translations(locale: \"%target%\") {\n" +
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

    public static String registerTransactionQuery() {
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
}
