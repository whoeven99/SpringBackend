package com.bogda.api.model.controller.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TranslateRequest implements Serializable {
    private int id;
    private String shopName;
    private String accessToken;
    private String source; //原语言
    private String target; //目标语言
    private String content; //要翻译的文本
}
