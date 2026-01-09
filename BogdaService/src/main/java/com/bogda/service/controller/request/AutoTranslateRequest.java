package com.bogda.service.controller.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class AutoTranslateRequest {
    private String shopName;
    private String source; //原语言
    private String target; //目标语言
    private Boolean autoTranslate;
    private String accessToken;
}
