package com.bogdatech.utils;

public class ShopifyRequestUtils {

    public static String getFirstQuery(String resourceType, String first, String target) {
        return "{\n" +
                "  translatableResources(resourceType: " + resourceType + ", first: " + first + ") {\n" +
                "    nodes {\n" +
                "      resourceId\n" +
                "      translations(locale: \"" + target + "\") {\n" +
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

    public static String getAfterQuery(String resourceType, String first, String target, String after) {
        return "{\n" +
                "  translatableResources(resourceType: " + resourceType + ", first: " + first + ", after: " + "\"" + after + "\"" + ") {\n" +
                "    nodes {\n" +
                "      resourceId" +
                "      translations(locale: \"" + target + "\") {\n" +
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
}
