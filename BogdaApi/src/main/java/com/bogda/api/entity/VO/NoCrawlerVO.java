package com.bogda.api.entity.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NoCrawlerVO {
    private String status;
    private String userIp;
    private String languageCode;
    private Boolean languageCodeStatus;
    private String currencyCode;
    private Boolean currencyCodeStatus;
    private String countryCode;
    private String costTime;
    private String ipApiCostTime;
    private String errorMessage;
}
