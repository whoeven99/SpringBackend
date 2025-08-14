package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.UserPrivateTranslateDO;

public interface IUserPrivateTranslateService extends IService<UserPrivateTranslateDO> {
    Boolean updateUserUsedCount(Integer apiKey, int length, String shopName, Long limit);
}
