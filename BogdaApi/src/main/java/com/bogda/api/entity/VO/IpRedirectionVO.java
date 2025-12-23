package com.bogda.api.entity.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class IpRedirectionVO {
    private Integer id;
    private String region;
    private String languageCode;
    private String currencyCode;
}
