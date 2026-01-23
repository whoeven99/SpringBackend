package com.bogda.common.controller.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TranslationCounterRequest {

    private int id;
    @NotBlank(message = "店铺名不能为空")
    private String shopName;
    private int chars;
    private int usedChars;
    private int googleChars;
    private int openAiChars;
    private int totalChars;

}
