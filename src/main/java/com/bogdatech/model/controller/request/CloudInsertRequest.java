package com.bogdatech.model.controller.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

import static com.bogdatech.constants.TranslateConstants.API_VERSION_LAST;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CloudInsertRequest {
    private String shopName;
    private String accessToken;
    private String apiVersion = API_VERSION_LAST;
    private String target;
    private Map<String, Object> body;
}
