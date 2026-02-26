package com.bogda.integration.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopifyPagesResponse {
    private PagesData data;
    private ShopifyExtensions extensions;

    @Data
    public static class PagesData {
        private ShopifyModuleConnection pages;
    }
}
