package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.TranslationCounterDO;
import com.bogdatech.entity.VO.AddCharsVO;
import com.bogdatech.model.controller.request.TranslationCounterRequest;

import java.sql.Timestamp;

public interface ITranslationCounterService extends IService<TranslationCounterDO> {
    TranslationCounterDO readCharsByShopName(String shopName);

    int insertCharsByShopName(TranslationCounterRequest translationCounterRequest);

    int updateUsedCharsByShopName(TranslationCounterRequest translationCounterRequest);

    Integer getMaxCharsByShopName(String shopName);

    Boolean updateCharsByShopName(String shopName, String accessToken, String gid, Integer chars);

    TranslationCounterDO getOneForUpdate(String shopName);

    Boolean updateAddUsedCharsByShopName(String shopName, Integer usedChars, Integer maxChars);

    Boolean deleteTrialCounter(String shopName);

    TranslationCounterDO getTranslationCounterByShopName(String shopName);

    Boolean updateUserCharsByShopName(String shopName, Integer chars);

    boolean updateFreeTrialDateByShopName(String shopName, Timestamp afterTrialDaysTimestamp, int i, Integer charsByPlan);
}
