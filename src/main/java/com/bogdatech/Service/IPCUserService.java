package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.PCUsersDO;

public interface IPCUserService extends IService<PCUsersDO> {
    PCUsersDO getUserByShopName(String shopName);

    boolean saveSingleUser(PCUsersDO pcUsersDO);

    boolean updateSingleUser(PCUsersDO pcUsersDO);

    boolean updatePurchasePointsByShopName(String shopName, Integer chars);

    boolean updateUsedPointsByShopName(String shopName, int picFee, Integer limitChars);

    boolean updateUninstallByShopName(String shopName);
}
