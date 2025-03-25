package com.bogdatech.model.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class KeywordModel {
    public String keyword;
    public String translation;
    public boolean caseSensitive;

}
