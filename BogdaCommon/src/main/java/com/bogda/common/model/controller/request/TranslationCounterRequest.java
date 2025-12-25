package com.bogda.common.model.controller.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TranslationCounterRequest {

    private int id;
    private String shopName;
    private int chars;
    private int usedChars;
    private int googleChars;
    private int openAiChars;
    private int totalChars;

}
