package com.bogdatech.model.controller.request;

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
    private String apiVersion = "2024-10";
    private String target;
    private Map<String, Object> body;
}
