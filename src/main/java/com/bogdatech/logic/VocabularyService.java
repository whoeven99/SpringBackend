package com.bogdatech.logic;

import com.bogdatech.Service.ITranslateTextService;
import com.bogdatech.Service.IVocabularyService;
import com.bogdatech.entity.TranslateTextDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VocabularyService {
    private final IVocabularyService vocabularyService;
    private final ITranslateTextService translateTextService;
    @Autowired
    public VocabularyService(IVocabularyService vocabularyService, ITranslateTextService translateTextService) {
        this.vocabularyService = vocabularyService;
        this.translateTextService = translateTextService;
    }
    //转换数据库表数据 老-》新
    public void storeTranslationsInVocabulary(){
        List<TranslateTextDO> translateTextList = translateTextService.getTranslateTextData();
        vocabularyService.storeTranslationsInVocabulary(translateTextList);
    }

    //读csv文件数据，存储到数据库中
    public void storeTranslationsInVocabularyByCsv(List<TranslateTextDO> translateTextList){
        vocabularyService.storeTranslationsInVocabulary(translateTextList);
    }

    //单独翻译online数据


}
