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
}
