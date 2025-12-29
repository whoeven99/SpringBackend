package com.bogda.api.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.api.entity.DO.UserTranslationDataDO;

public interface IUserTranslationDataService extends IService<UserTranslationDataDO> {
    Boolean insertTranslationData(String translationData, String shopName);
}
