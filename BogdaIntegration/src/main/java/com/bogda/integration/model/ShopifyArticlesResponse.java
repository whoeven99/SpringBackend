package com.bogda.integration.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopifyArticlesResponse {
    private ArticlesData data;
    private ShopifyExtensions extensions;

    @Data
    public static class ArticlesData {
        private ShopifyModuleConnection articles;
    }
}
