package com.bogdatech.logic;

import com.bogdatech.Service.IUserTranslationDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserTranslationDataService {
    @Autowired
    private IUserTranslationDataService userTranslationDataService;
    /**
     * 将翻译后的文本以String的类型存储到数据库中
     */
    public Boolean insertTranslationData(String translationData, String shopName) {
        return userTranslationDataService.insertTranslationData(translationData, shopName);
    }
}
