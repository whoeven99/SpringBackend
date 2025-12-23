package com.bogda.common.controller.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TranslateSuccessRequest {
    private String target;
    private String chars;
    private String time;
    private String allChars;
}
