package com.bogda.service.controller.request;

import com.bogda.common.contants.TranslateConstants;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CloudServiceRequest {

    private String shopName;

    private String accessToken;

    private String apiVersion = TranslateConstants.API_VERSION_LAST;

    private String target;

    private String body;
}
