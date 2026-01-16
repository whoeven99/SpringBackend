package com.bogda.service.controller.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TargetListRequest {
    private List<String> targetList;
    private String shopName;
    private String accessToken;
    private String source;
}
