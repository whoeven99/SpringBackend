package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.UserIpDO;

public interface IUserIpService extends IService<UserIpDO> {
    Boolean addOrUpdateUserIp(String shopName);

    UserIpDO selectByShopNameForUpdate(String shopName);
}
