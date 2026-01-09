package com.bogda.service.controller.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegisterTransactionRequest {
    private String shopName;
    private String accessToken;
    private String locale;
    private String key;
    private String value;
    private String translatableContentDigest;
    private String resourceId;
    private String target;
}
