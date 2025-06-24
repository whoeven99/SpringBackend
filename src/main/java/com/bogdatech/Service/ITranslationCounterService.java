package com.bogdatech.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.TranslationCounterDO;
import com.bogdatech.model.controller.request.TranslationCounterRequest;

public interface ITranslationCounterService extends IService<TranslationCounterDO> {
    TranslationCounterDO readCharsByShopName(String shopName);
    int insertCharsByShopName(TranslationCounterRequest translationCounterRequest);
    int updateUsedCharsByShopName(TranslationCounterRequest translationCounterRequest);

    Integer getMaxCharsByShopName(String shopName);

    Boolean updateCharsByShopName(TranslationCounterRequest request);

    TranslationCounterDO getOneForUpdate(String shopName);

     Boolean updateAddUsedCharsByShopName(String shopName, Integer usedChars, Integer maxChars);
}
