package com.bogdatech.query;

import com.bogdatech.entity.TranslateResourceDTO;

public class ShopifyQuery {

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

}
