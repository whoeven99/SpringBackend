package com.bogdatech.logic;

import com.bogdatech.Service.IUserLiquidService;
import com.bogdatech.entity.DO.UserLiquidDO;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class UserLiquidService {
    @Autowired
    private IUserLiquidService iUserLiquidService;

    public BaseResponse<Object> selectShopNameLiquidData(String shopName, UserLiquidDO userLiquidDO) {
        // 主要是shopname、source、key3个值，其中key可传可不传
        List<UserLiquidDO> userLiquidDOS = iUserLiquidService.selectLiquidData(shopName, userLiquidDO.getLanguageCode()
                , userLiquidDO.getLiquidId());
        return new BaseResponse<>().CreateSuccessResponse(userLiquidDOS);
    }

    public BaseResponse<Object> insertShopNameLiquidData(String shopName, UserLiquidDO userLiquidDO) {
        // 判断这个值在数据库中是否存在， 不存在插入；存在，更新
        userLiquidDO.setShopName(shopName);
        UserLiquidDO liquidData = iUserLiquidService.getLiquidData(shopName, userLiquidDO.getLanguageCode(), userLiquidDO.getLiquidId()
                , userLiquidDO.getLiquidBeforeTranslation());
        if (liquidData == null) {
            boolean save = iUserLiquidService.save(userLiquidDO);
            return new BaseResponse<>().CreateSuccessResponse(save);
        }

        boolean updateFlag = iUserLiquidService.updateLiquidData(shopName, userLiquidDO);
        return new BaseResponse<>().CreateSuccessResponse(updateFlag);
    }

    public BaseResponse<Object> parseLiquidDataByShopNameAndLanguage(String shopName, String language) {
        List<UserLiquidDO> userLiquidDOS = iUserLiquidService.selectLiquidData(shopName, language, null);
        return new BaseResponse<>().CreateSuccessResponse(userLiquidDOS);
    }
}
