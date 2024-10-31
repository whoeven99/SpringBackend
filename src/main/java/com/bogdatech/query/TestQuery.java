package com.bogdatech.query;

import com.bogdatech.entity.TranslateResourceDTO;

public class TestQuery {

    public String getTestQuery(TranslateResourceDTO translateResourceDTO){
        String testQuery = "{\n" +
                "  translatableResources(resourceType: " + translateResourceDTO.getResourceType() + ", first: " + translateResourceDTO.getFirst() + ", after: " + translateResourceDTO.getAfter() + ") {\n" +
                "    nodes {\n" +
                "      translations(locale: \""+ translateResourceDTO.getTarget() +"\") {\n" +
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
    public static final String TEST_QUERY = "{" +
            "    shopLocales {" +
            "        name" +
            "        locale" +
            "        primary" +
            "        published" +
            "    }" +
            "}";
}
