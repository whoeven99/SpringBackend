package com.bogda.api.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.api.entity.DO.UserIpDO;

public interface IUserIpService extends IService<UserIpDO> {
    Boolean addOrUpdateUserIp(String shopName);

    UserIpDO selectByShopNameForUpdate(String shopName);

    Long getIpCountByShopName(String shopName);

    boolean clearIP(String shopName);
}
