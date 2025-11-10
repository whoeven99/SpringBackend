package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.UserLiquidDO;

import java.util.List;
import java.util.Map;

public interface IUserLiquidService extends IService<UserLiquidDO> {
    List<UserLiquidDO> selectLiquidData(String shopName);

    UserLiquidDO getLiquidData(String shopName, String languageCode, String liquidBeforeTranslation);

    boolean updateLiquidData(String shopName, UserLiquidDO userLiquidDO);

    List<UserLiquidDO> selectLiquidDataByShopNameAndLanguageCode(String shopName, String languageCode);
}
