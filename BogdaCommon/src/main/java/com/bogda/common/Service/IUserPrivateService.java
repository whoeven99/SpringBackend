package com.bogda.common.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.common.entity.DO.UserPrivateDO;

public interface IUserPrivateService extends IService<UserPrivateDO> {
    UserPrivateDO selectOneByShopName(String shopName);

    Boolean updateAmountAndGoogleKey(String shopName);
}
