package com.bogdatech.model.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class KeywordModel {
    String keyword;
    String translation;
    boolean caseSensitive;

}
