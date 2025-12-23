package com.bogda.common.controller.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class BasicRateRequest {
    private String source ; //原币种编号
    //目标币种编号
    private String target ; //目标币种编号
}
