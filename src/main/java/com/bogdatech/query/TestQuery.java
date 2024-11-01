package com.bogdatech.query;

import com.bogdatech.entity.TranslateResourceDTO;

public class TestQuery {

    public String getTestQuery(TranslateResourceDTO translateResourceDTO) {
        String testQuery = "{\n" +
                "  translatableResources(resourceType: " + translateResourceDTO.getResourceType() + ", first: " + translateResourceDTO.getFirst() + ") {\n" +
                "    nodes {\n" +
                "      resourceId " +
                "      translations(locale: \"" + translateResourceDTO.getTarget() + "\") {\n" +
                "        locale\n" +
                "        value\n" +
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
        return testQuery;
    }

    public String getAfterQuery(TranslateResourceDTO translateResourceDTO) {
        String testQuery = "{\n" +
                "  translatableResources(resourceType: " + translateResourceDTO.getResourceType() + ", first: " + translateResourceDTO.getFirst() + ", after: " + "\"" + translateResourceDTO.getAfter() + "\"" + ") {\n" +
                "    nodes {\n" +
                "      translations(locale: \"" + translateResourceDTO.getTarget() + "\") {\n" +
                "        locale\n" +
                "        value\n" +
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
        return testQuery;
    }

    public String registerTransactionQuery() {
        String registerTransactionQuery = "mutation translationsRegister($resourceId: ID!, $translations: [TranslationInput!]!) {\n" +
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
        return registerTransactionQuery;
    }

    public static final String TEST_QUERY = "{" +
            "    shopLocales {" +
            "        name" +
            "        locale" +
            "        primary" +
            "        published" +
            "    }" +
            "}";
}
