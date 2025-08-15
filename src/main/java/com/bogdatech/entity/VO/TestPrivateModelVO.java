package com.bogdatech.entity.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TestPrivateModelVO {
    private Integer apiName;
    private String sourceText;
    private String targetCode;
}
