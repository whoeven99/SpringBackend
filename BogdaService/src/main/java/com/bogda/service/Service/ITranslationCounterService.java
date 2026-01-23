package com.bogda.service.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.common.entity.DO.TranslationCounterDO;

public interface ITranslationCounterService extends IService<TranslationCounterDO> {
    TranslationCounterDO readCharsByShopName(String shopName);

    int insertCharsByShopName(String shopName);

    Integer getMaxCharsByShopName(String shopName);

    Boolean updateCharsByShopName(String shopName, String accessToken, String gid, Integer chars);

    TranslationCounterDO getOneForUpdate(String shopName);

    Boolean deleteTrialCounter(String shopName);

    TranslationCounterDO getTranslationCounterByShopName(String shopName);
}
