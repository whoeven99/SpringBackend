package com.bogda.common.entity.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AltTranslateVO {
    private String alt;
    private String targetCode;
    private String accessToken;
}
