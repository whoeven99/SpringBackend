package com.bogda.common.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.common.entity.DO.UserLiquidDO;

import java.util.List;

public interface IUserLiquidService extends IService<UserLiquidDO> {
    List<UserLiquidDO> selectLiquidData(String shopName);

    UserLiquidDO getLiquidData(String shopName, String liquidAfterTranslation, String languageCode, String liquidBeforeTranslation);

    boolean updateLiquidDataById(UserLiquidDO userLiquidDO);

    List<UserLiquidDO> selectLiquidDataByShopNameAndLanguageCode(String shopName, String languageCode);

    boolean deleteLiquidDataByIds(String shopName, List<Integer> ids);

    boolean updateReplacementMethodById(Integer id, boolean b);
}
