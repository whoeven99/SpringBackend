package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.UserPrivateTranslateDO;

public interface IUserPrivateTranslateService extends IService<UserPrivateTranslateDO> {
    Boolean updateUserUsedCount(Integer apiKey, int length, String shopName, Long limit);

    UserPrivateTranslateDO getPrivateDataByShopNameAndUserKey(String shopName, String userKey);

    UserPrivateTranslateDO getPrivateDataByShopNameAndApiName(String shopName, Integer apiName);

    Boolean updateUserDataByShopNameAndApiName(String shopName, Integer apiName, String apiModel, String promptWord, Long tokenLimit, Boolean isSelected);
}
