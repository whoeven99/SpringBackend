package com.bogda.integration.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShopifyRegisterResponse {
    private ShopifyGraphRegisterResponse data;
    private ShopifyExtensions extensions;
}
