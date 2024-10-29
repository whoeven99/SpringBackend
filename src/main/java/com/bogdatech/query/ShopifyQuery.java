package com.bogdatech.query;

public class ShopifyQuery {
    public static final String PRODUCT_QUERY = "{\n" +
            "  products(first: 3) {\n" +
            "    nodes {\n" +
            "      handle #翻译\n" +
            "      id #产品id\n" +
            "      descriptionHtml #翻译\n" +
            "      seo {\n" +
            "        description #翻译\n" +
            "        title #翻译\n" +
            "      }\n" +
            "      productType #翻译\n" +
            "      options(first: 3) {\n" +
            "        name #选项名\n" +
            "        values #翻译\n" +
            "      }\n" +
            "      metafields(first: 3) {\n" +
            "        nodes {\n" +
            "          id #元字段id\n" +
            "          definition {\n" +
            "            type {\n" +
            "              category #元字段类型\n" +
            "            }\n" +
            "          }\n" +
            "          value #翻译\n" +
            "        }\n" +
            "      }\n" +
            "      title #翻译\n" +
            "    }\n" +
            "  }\n" +
            "}";

    public static final String PRODUCT2_QUERY = "{\n" +
            "  translatableResources(resourceType: PRODUCT, first: 2) {\n" +
            "    nodes {\n" +
            "      translations(locale: \"zh\") {\n" +
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
}
