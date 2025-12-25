package com.bogda.common.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.common.entity.DO.UserTranslationDataDO;

public interface IUserTranslationDataService extends IService<UserTranslationDataDO> {
    Boolean insertTranslationData(String translationData, String shopName);
}
