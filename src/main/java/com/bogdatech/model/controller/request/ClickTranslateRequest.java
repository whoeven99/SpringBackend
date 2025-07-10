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
    private Boolean isCover; //是否覆盖
    private String translateSettings1; //模型 前端定的参数
    private String translateSettings2; //语言包，先不管
    private List<String> translateSettings3; //模块类型
    private String customKey; //自定义key
}
