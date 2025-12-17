package com.bogdatech.utils;

public class ShopifyRequestUtils {
    public static String getQuery(String resourceType, String first, String target) {
        return "query MyQuery {\n" +
                "  translatableResources(resourceType: " + resourceType + ", first: " + first + ") {\n" +
                query.replace("%target%", target);
    }

    public static String getQuery(String resourceType, String first, String target, String after) {
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
}
