package com.bogdatech.model.controller.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class BasicRateRequest {
    //原币种编号
    // TODO 是暴露给自己前端的，参数名称可以全一点方便理解
    private String source ; //原币种编号
    //目标币种编号
    private String target ; //目标币种编号
}
