package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.VocabularyDO;

public interface IVocabularyService extends IService<VocabularyDO> {
    /**
     * 将Translations存入Vocabulary
     */
    void storeTranslationsInVocabulary();

    void storeTranslationsInVocabularyByCsv();
}
