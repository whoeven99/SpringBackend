package com.bogda.service.entity.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ImageTranslateVO {
    private String imageUrl;
    private String sourceCode;
    private String targetCode;
    private String accessToken;
    private Integer modelType; // 1. aidge_standard   2. huoshan  3. aidge_pro
}
