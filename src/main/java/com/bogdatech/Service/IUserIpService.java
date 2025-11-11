package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.UserIpDO;

public interface IUserIpService extends IService<UserIpDO> {
    Boolean addOrUpdateUserIp(String shopName);

    UserIpDO selectByShopNameForUpdate(String shopName);

    Boolean updateIpByFirstEmailAndShopName(UserIpDO userIpDO,String shopName, boolean b);

    Boolean updateIpBySecondEmailAndShopName(UserIpDO userIpDO, String shopName, boolean b);

    boolean resetUsersFreeIp(String shopName);
}
