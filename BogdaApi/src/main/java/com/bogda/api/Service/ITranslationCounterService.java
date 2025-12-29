package com.bogda.api.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.api.entity.DO.TranslationCounterDO;
import com.bogda.api.model.controller.request.TranslationCounterRequest;

public interface ITranslationCounterService extends IService<TranslationCounterDO> {
    TranslationCounterDO readCharsByShopName(String shopName);

    int insertCharsByShopName(TranslationCounterRequest translationCounterRequest);

    Integer getMaxCharsByShopName(String shopName);

    Boolean updateCharsByShopName(String shopName, String accessToken, String gid, Integer chars);

    TranslationCounterDO getOneForUpdate(String shopName);

    Boolean deleteTrialCounter(String shopName);

    TranslationCounterDO getTranslationCounterByShopName(String shopName);
}
