package com.bogdatech.model.controller.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class BasicRateRequest {
    //使用API的唯一凭证
    public final static String APP_KEY = "74178";
    //md5后的32位密文,登陆用
    public final static String SIGN = "d2fd0dd07b86c05658392bd4e5bc3a63";
    //原币种编号
    private String scur ;
    //目标币种编号
    private String tcur ;

}
