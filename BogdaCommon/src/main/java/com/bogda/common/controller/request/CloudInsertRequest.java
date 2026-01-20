package com.bogda.common.controller.request;

import com.bogda.common.contants.TranslateConstants;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CloudInsertRequest {
    private String shopName;
    private String accessToken;
    private String apiVersion = TranslateConstants.API_VERSION_LAST;
    private String target;
    private Map<String, Object> body;
}
