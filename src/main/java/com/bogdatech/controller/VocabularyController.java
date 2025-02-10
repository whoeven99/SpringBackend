package com.bogdatech.controller;

import com.bogdatech.logic.VocabularyService;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/vocabulary")
public class VocabularyController {
    //将数据库当前有的数据存储到新的表格
    @Autowired
    private VocabularyService vocabularyService;
    @PostMapping("/convertData")
    public BaseResponse<Object> convertData() {
        vocabularyService.storeTranslationsInVocabulary();
        return null;
    }
}
