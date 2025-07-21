package com.bogdatech.model.controller.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import static com.bogdatech.constants.TranslateConstants.API_VERSION_LAST;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CloudServiceRequest {

    private String shopName;

    private String accessToken;

    private String apiVersion = API_VERSION_LAST;

    private String target;

    private String body;
}
