package com.bogda.api.model.controller.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TranslateTextRequest {

    private int id;
    private String shopName;
    private String resourceId;
    private String textType;
    private String digest;
    private String textKey;
    private String sourceText;
    private String targetText;
    private String sourceCode;
    private String targetCode;
}
