package com.bogdatech.entity.VO;

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
}
