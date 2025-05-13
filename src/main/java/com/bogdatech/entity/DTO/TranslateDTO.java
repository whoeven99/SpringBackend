package com.bogdatech.entity.DTO;

import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.utils.CharacterCountUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class TranslateDTO {
    private TranslateRequest translateRequest;
    private Integer remainingChars;
    private Integer usedChars;
    private CharacterCountUtils counter;
    private Boolean isTask;
    private List<String> translateModels;
}
