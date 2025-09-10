package com.bogdatech.entity.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TestVO {
    private String target;
    private String packId;
    private String translationKeyType;
    private String modelType;
    private String value;
}
