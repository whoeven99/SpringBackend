package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.TranslateTextDO;
import com.bogdatech.entity.VocabularyDO;

import java.util.List;

public interface IVocabularyService extends IService<VocabularyDO> {
    /**
     * 将Translations存入Vocabulary
     */

    // 用于存储翻译到 VocabularyDO 表
    void storeTranslationsInVocabulary(List<TranslateTextDO> translateTextList);

    String getTranslateTextDataInVocabulary(String target, String value, String source);

    void testInsert(String target, String value, String source);

    Integer InsertOne(String target, String targetValue, String source, String sourceValue);

}
