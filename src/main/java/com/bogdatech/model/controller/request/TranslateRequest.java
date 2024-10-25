package com.bogdatech.model.controller.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TranslateRequest {
    private String shopName;
    private String accessToken;
    private String locale;
    private String source; //原语言
    private String target; //目标语言
    private String content; //要翻译的文本
}
