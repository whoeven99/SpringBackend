package com.bogdatech.model.controller.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShopifyRequest {

    private String shopName;

    private String accessToken;

    private String apiVersion = "2024-10";

    private String target;


}
