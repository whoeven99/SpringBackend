package com.bogdatech.logic;

import com.bogdatech.Service.IVocabularyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VocabularyService {
    @Autowired
    private IVocabularyService vocabularyService;

    //转换数据库表数据 老-》新
    public void storeTranslationsInVocabulary(){
        vocabularyService.storeTranslationsInVocabulary();
    }

    //读csv文件数据，存储到数据库中
    public void storeTranslationsInVocabularyByCsv(){
        vocabularyService.storeTranslationsInVocabularyByCsv();
    }

    //单独翻译online数据


}
