package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.UserTranslationDataDO;

import java.util.List;

public interface IUserTranslationDataService extends IService<UserTranslationDataDO> {
    Boolean insertTranslationData(String translationData, String shopName);

    List<UserTranslationDataDO> selectTranslationDataList();

    List<UserTranslationDataDO> selectWritingDataByShopNameAndSourceAndTarget(String shopName, String target);
}
