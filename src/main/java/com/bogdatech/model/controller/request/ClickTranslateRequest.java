package com.bogdatech.model.controller.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClickTranslateRequest {
    private int id;
    private String shopName;
    private String accessToken;
    private String source; //原语言
    private String target; //目标语言
    private String content; //要翻译的文本

    private String translateSettings1;
    private String translateSettings2;
    private List<String> translateSettings3;
}
