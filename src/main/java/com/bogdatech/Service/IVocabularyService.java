package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.TranslateTextDO;
import com.bogdatech.entity.VocabularyDO;

import java.util.List;

public interface IVocabularyService extends IService<VocabularyDO> {
    /**
     * 将Translations存入Vocabulary
     */
    void storeTranslationsInVocabulary(List<TranslateTextDO> translateTextList);

}
