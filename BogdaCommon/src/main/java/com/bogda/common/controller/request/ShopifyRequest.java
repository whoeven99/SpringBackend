package com.bogda.common.controller.request;

import com.bogda.common.contants.TranslateConstants;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShopifyRequest {

    private String shopName;

    private String accessToken;

    private String apiVersion = TranslateConstants.API_VERSION_LAST;

    private String target;



}
