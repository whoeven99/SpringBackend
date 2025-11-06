package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.UserLiquidDO;

import java.util.List;

public interface IUserLiquidService extends IService<UserLiquidDO> {
    List<UserLiquidDO> selectLiquidData(String shopName, String languageCode, String liquidId);

    UserLiquidDO getLiquidData(String shopName, String languageCode, String liquidId, String liquidBeforeTranslation);

    boolean updateLiquidData(String shopName, UserLiquidDO userLiquidDO);
}
