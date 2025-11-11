package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.APGUsersDO;

public interface IAPGUsersService extends IService<APGUsersDO> {
    APGUsersDO getUserByShopName(String shopName);
}
