package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.TranslationCounterDO;
import com.bogdatech.model.controller.request.TranslationCounterRequest;

public interface ITranslationCounterService extends IService<TranslationCounterDO> {
    public TranslationCounterDO readCharsByShopName(TranslationCounterRequest request);
    public int insertCharsByShopName(TranslationCounterRequest translationCounterRequest);
    public int updateUsedCharsByShopName(TranslationCounterRequest translationCounterRequest);
}