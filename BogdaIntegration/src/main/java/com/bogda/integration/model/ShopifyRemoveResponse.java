package com.bogda.integration.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopifyRemoveResponse {
    private ShopifyGraphRemoveResponse data;
    private ShopifyExtensions extensions;
}
