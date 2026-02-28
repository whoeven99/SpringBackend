package com.bogda.integration.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShopifyGraphResponse {
    /** 与 Shopify API 一致：data.translatableResources 直接为 { nodes, pageInfo } */
    private ShopifyTranslationsResponse translatableResources;
    private ShopifyExtensions extensions;
}
