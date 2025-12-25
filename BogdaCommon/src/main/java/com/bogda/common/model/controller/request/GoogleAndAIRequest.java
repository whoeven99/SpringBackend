package com.bogda.common.model.controller.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GoogleAndAIRequest {
    private String promotWord;
    private Integer deductionRate;
    private String source;
    private String target;
    private String content;

}
