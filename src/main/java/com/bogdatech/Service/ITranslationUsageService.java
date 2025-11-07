package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.TranslatesDO;
import com.bogdatech.entity.DO.TranslationUsageDO;

import java.util.List;

public interface ITranslationUsageService extends IService<TranslationUsageDO> {
    List<TranslationUsageDO> readTranslationUsageData(String shopName);

    void insertOrUpdateSingleData(TranslationUsageDO translationUsageDO);

    Boolean judgeSendAutoEmail(List<TranslatesDO> translatesDOList, String shopName);

    void insertListData(List<TranslatesDO> list, String shopName);

    boolean updateUsageDataByShopName(String shopName, int status, int remainingCredits, int consumedTime, int creditCount);

    boolean updateUsageToCompleteByShopNameAndTarget(String shopName, String target, long costTime, int usedChars, int endChars, int i);
}
