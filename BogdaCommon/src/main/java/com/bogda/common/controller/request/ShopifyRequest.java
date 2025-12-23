package com.bogda.common.controller.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import static com.bogda.common.constants.TranslateConstants.API_VERSION_LAST;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShopifyRequest {

    private String shopName;

    private String accessToken;

    private String apiVersion = API_VERSION_LAST;

    private String target;



}
