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

}
