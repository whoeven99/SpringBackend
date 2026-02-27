package com.bogda.common.utils;

public class ShopifyRequestUtils {
    public static String getQuery(String resourceType, String first, String target) {
        return getQuery(resourceType, first, target, null);
    }

    public static String getQuery(String resourceType, String first, String target, String after) {
        String afterClause = (after == null || after.isEmpty())
                ? ""
                : ", after: \"%s\"".formatted(after);

        return QUERY.formatted(resourceType, first, afterClause, target);
    }

    public static final String QUERY = """
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

    /** 获取 Product ID 列表（用于 translatableResourcesByIds），支持 query 筛选如 status:active、updated_at:>xxx；返回 edges 以匹配 ShopifyProductsResponse */
    public static final String PRODUCTS_IDS_QUERY = """
            query GetProducts($query: String, $first: Int, $after: String) {
              products(first: $first, after: $after, query: $query) {
                edges { node { id } }
                pageInfo { endCursor hasNextPage hasPreviousPage startCursor }
              }
            }
            """;

    /** 获取 Article ID 列表，支持 query 筛选；返回 edges 以匹配 ShopifyArticlesResponse */
    public static final String ARTICLES_IDS_QUERY = """
            query GetArticles($query: String, $first: Int, $after: String) {
              articles(first: $first, after: $after, query: $query) {
                edges { node { id } }
                pageInfo { endCursor hasNextPage hasPreviousPage startCursor }
              }
            }
            """;

    /** 获取 Page ID 列表，支持 query 筛选；返回 edges 以匹配 ShopifyPagesResponse */
    public static final String PAGES_IDS_QUERY = """
            query GetPages($query: String, $first: Int, $after: String) {
              pages(first: $first, after: $after, query: $query) {
                edges { node { id } }
                pageInfo { endCursor hasNextPage hasPreviousPage startCursor }
              }
            }
            """;

    /** 获取 Collection ID 列表，支持 query 筛选；返回 edges 以匹配 ShopifyCollectionsResponse */
    public static final String COLLECTIONS_IDS_QUERY = """
            query GetCollections($query: String, $first: Int, $after: String) {
              collections(first: $first, after: $after, query: $query) {
                edges { node { id } }
                pageInfo { endCursor hasNextPage hasPreviousPage startCursor }
              }
            }
            """;

    /** translatableResourcesByIds：根据资源 ID 列表查询可翻译内容，仅返回 outdated 由调用方在 needTranslate 中过滤 */
    public static final String TRANSLATABLE_RESOURCES_BY_IDS_QUERY = """
            query GetTranslatableResourcesByIds($resourceIds: [ID!]!, $first: Int, $after: String, $locale: String!) {
              translatableResourcesByIds(resourceIds: $resourceIds, first: $first, after: $after) {
                nodes {
                  resourceId
                  translations(locale: $locale) {
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
     * 删除用户shopify数据
     */
    public static String deleteQuery() {
        return """
                mutation translationsRemove($resourceId: ID!, $translationKeys: [String!]!, $locales: [String!]!) {
                  translationsRemove(resourceId: $resourceId, translationKeys: $translationKeys, locales: $locales) {
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

    /**
     * 创建 storefrontAccessToken
     */
    public static String createAccessTokenQuery() {
        return """
                mutation storefrontAccessTokenCreate($input: StorefrontAccessTokenInput!) {
                                    storefrontAccessTokenCreate(input: $input) {
                                    shop { id }
                                    storefrontAccessToken { accessToken title }
                                    userErrors { field message }
                                        }
                                    }
                """;
    }

    /**
     * 查询用户名和邮件信息
     */
    public static String queryShopOwner() {
        return """
                query { shop { shopOwnerName email } }
                """;
    }
}
