package com.bogda.api.utils;

public class ShopifyRequestUtils {
    public static String getQuery(String resourceType, String first, String target) {
        return "query MyQuery {\n" +
                "  translatableResources(resourceType: " + resourceType + ", first: " + first + ") {\n" +
                query.replace("%target%", target);
    }

    public static String getQuery(String resourceType, String first, String target, String after) {
        if (after == null || after.isEmpty()) {
            return getQuery(resourceType, first, target);
        }
        return "query MyQuery {\n" +
                "  translatableResources(resourceType: " + resourceType + ", first: " + first + ", after: " + "\"" + after + "\"" + ") {\n" +
                query.replace("%target%", target);
    }

    public static String query = """
                nodes {
                  resourceId
                  translations(locale: "%target%") {
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

    public static String getLanguagesQuery() {
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

    /**
     * 直接根据 resourceId 查询翻译内容
     * 使用 Shopify GraphQL API 的 translations 查询
     * 
     * @param resourceId 资源的全局唯一标识符，格式如: "gid://shopify/Product/1234567890"
     * @param locale 目标语言代码，如: "fr", "zh-CN"
     * @return GraphQL 查询字符串
     * 
     * 文档参考: https://shopify.dev/docs/api/admin-graphql/latest/queries/translations
     */
    public static String getTranslationsByResourceIdQuery(String resourceId, String locale) {
        return """
                query MyQuery {
                  translations(resourceId: "%s", locale: "%s") {
                    key
                    value
                    locale
                    outdated
                  }
                }
                """.formatted(resourceId, locale);
    }
}
