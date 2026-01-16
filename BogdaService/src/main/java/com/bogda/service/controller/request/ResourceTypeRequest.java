package com.bogda.service.controller.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResourceTypeRequest {
    private String shopName;
    private String accessToken;
    private String resourceType;
    private String target;
    private String source;
}
