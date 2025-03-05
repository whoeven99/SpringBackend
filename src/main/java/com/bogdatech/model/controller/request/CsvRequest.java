package com.bogdatech.model.controller.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CsvRequest {

    private String source_code;
    private String source_text;
    private String target_code;
    private String target_text;
    private String key;
}
