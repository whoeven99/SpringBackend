package com.bogda.integration.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对应 Shopify Admin API translatableResourcesByIds 的完整响应：{ "data": { "translatableResourcesByIds": { "nodes", "pageInfo" } }, "extensions" }。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopifyTranslatableResourcesByIdsResponse {
    private TranslatableResourcesByIdsData data;
    private ShopifyExtensions extensions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TranslatableResourcesByIdsData {
        private ShopifyTranslationsResponse translatableResourcesByIds;
    }
}
