package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.UserPrivateDO;

public interface IUserPrivateService extends IService<UserPrivateDO> {
    UserPrivateDO selectOneByShopName(String shopName);

    Integer addOrUpdateGoogleUserData(String shopName, String googleKey, Integer amount);

    Integer getUserId(String shopName);
}
