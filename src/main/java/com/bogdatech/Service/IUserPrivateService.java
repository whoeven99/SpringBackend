package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.UserPrivateDO;

public interface IUserPrivateService extends IService<UserPrivateDO> {
    UserPrivateDO selectOneByShopName(String shopName);

    Integer addOrUpdateGoogleUserData(String shopName, String googleKey, Integer amount);

    Integer getUserId(String shopName);

    Boolean updateAmountAndGoogleKey(String shopName);

    boolean updatePrivateUserByShopName(UserPrivateDO userPrivateDO, String shopName);
}
