package com.bogdatech.entity.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TranslationCharsVO {
    private String subGid;
    private String accessToken;
    private Integer feeType; // 0是月费； 1是年费
}
