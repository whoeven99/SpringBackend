package com.bogda.common.utils;

public class ShopifyRequestUtils {
    public static String getQuery(String resourceType, String first, String target) {
        return getQuery(resourceType, first, target, null);
    }

    public static String getQuery(String resourceType, String first, String target, String after) {
        String afterClause = (after == null || after.isEmpty())
                ? ""
                : ", after: \"%s\"".formatted(after);

        return query.formatted(resourceType, first, afterClause, target);
    }

    public static final String query = """
                query MyQuery {
                  translatableResources(resourceType: %s, first: %s%s) {
                    nodes {
                      resourceId
                      translations(locale: "%s") {
                        locale
                        value
                        key
                        outdated
                      }
                      translatableContent {
                        type
                        locale
                        key
                        value
                        digest
                      }
                    }
                    pageInfo {
                      endCursor
                      hasNextPage
                    }
                  }
                }
            """;

    /**
     * 根据产品id获取对应信息
     */
    public static String getProductDataQuery(String productId) {
        return """
                {
                  product(id: "%s") {
                    descriptionHtml
                    id
                    media(first: 1) {
                      edges {
                        node {
                          ... on MediaImage {
                            image {
                              url
                              altText
                            }
                          }
                        }
                      }
                    }
                    productType
                    title
                  }
                }
                """.formatted(productId);
    }

    public static String registerTransactionQuery() {
        return """
                    mutation translationsRegister($resourceId: ID!, $translations: [TranslationInput!]!) {
                      translationsRegister(resourceId: $resourceId, translations: $translations) {
                        userErrors {
                          message
                          field
                        }
                        translations {
                          key
                          value
                        }
                      }
                    }
                """;
    }

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

    public static String getLanguagesQuery() {
        return """
                  query MyQuery {
                    shopLocales {
                      locale
                      name
                      primary
                      published
                    }
                  }
                  """;
    }

    /**
     * 判断元字段id 是否关联到product
     */
    public static String getQueryForCheckMetafieldId(String metafieldId) {
        return """
            query MyQuery {
              node(id: "%s") {
                ... on Metafield {
                  id
                  owner {
                    ... on Product {
                      id
                      title
                    }
                  }
                  type
                }
              }
            }
            """.formatted(metafieldId);
    }
}
