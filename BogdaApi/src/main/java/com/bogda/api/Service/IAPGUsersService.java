package com.bogda.api.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.api.entity.DO.APGUsersDO;

import java.sql.Timestamp;

public interface IAPGUsersService extends IService<APGUsersDO> {
    APGUsersDO getUserByShopName(String shopName);

    boolean uninstallUser(String shopName, Timestamp now);
}
