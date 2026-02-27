package com.bogda.integration.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShopifyPagesResponse {
    private PagesData data;
    private ShopifyExtensions extensions;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PagesData {
        private ShopifyModuleConnection pages;
    }
}
