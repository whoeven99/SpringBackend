package com.bogda.service.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.service.entity.DO.APGUsersDO;

import java.sql.Timestamp;

public interface IAPGUsersService extends IService<APGUsersDO> {
    APGUsersDO getUserByShopName(String shopName);

    boolean uninstallUser(String shopName, Timestamp now);
}
