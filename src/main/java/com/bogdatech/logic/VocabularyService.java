package com.bogdatech.logic;

import com.bogdatech.Service.IVocabularyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VocabularyService {
    @Autowired
    private IVocabularyService vocabularyService;

    public void storeTranslationsInVocabulary(){
        vocabularyService.storeTranslationsInVocabulary();
    }

}
