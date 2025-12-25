package com.bogda.common.model.controller.request;

import com.bogda.common.constants.TranslateConstants;
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
