package com.bogdatech.logic;

import com.bogdatech.Service.IUserLiquidService;
import com.bogdatech.entity.DO.UserLiquidDO;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.utils.JsonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class UserLiquidService {
    @Autowired
    private IUserLiquidService iUserLiquidService;

    public BaseResponse<Object> selectShopNameLiquidData(String shopName) {
        // 第一版 只传shopName
        List<UserLiquidDO> userLiquidList = iUserLiquidService.selectLiquidData(shopName);
        return new BaseResponse<>().CreateSuccessResponse(userLiquidList);
    }

    public BaseResponse<Object> insertShopNameLiquidData(String shopName, UserLiquidDO userLiquidDO) {
        // 判断这个值在数据库中是否存在， 不存在插入；存在，更新
        if (shopName == null || userLiquidDO == null) {
            return new BaseResponse<>().CreateErrorResponse("Invalid input parameters");
        }

        userLiquidDO.setShopName(shopName);

        // 查询是否存在
        UserLiquidDO existing = iUserLiquidService.getLiquidData(
                shopName,
                userLiquidDO.getLanguageCode(),
                userLiquidDO.getLiquidBeforeTranslation()
        );

        boolean isSuccess;
        UserLiquidDO resultData;

        if (existing == null) {
            // 插入逻辑
            isSuccess = iUserLiquidService.save(userLiquidDO);
            resultData = isSuccess
                    ? iUserLiquidService.getLiquidData(shopName, userLiquidDO.getLanguageCode(), userLiquidDO.getLiquidBeforeTranslation())
                    : null;
        } else {
            // 更新逻辑（保持 id 一致）
            userLiquidDO.setId(existing.getId());
            isSuccess = iUserLiquidService.updateLiquidDataById(userLiquidDO);
            resultData = isSuccess ? userLiquidDO : null;
        }

        if (isSuccess) {
            return new BaseResponse<>().CreateSuccessResponse(resultData);
        }
        return new BaseResponse<>().CreateErrorResponse(null, "Database operation failed");
    }

    public BaseResponse<Object> parseLiquidDataByShopNameAndLanguage(String shopName, String languageCode) {
        List<UserLiquidDO> userLiquids = iUserLiquidService.selectLiquidDataByShopNameAndLanguageCode(shopName, languageCode);
        Map<String, String> resultMap = new LinkedHashMap<>();
        if (userLiquids == null || userLiquids.isEmpty()) {
            return new BaseResponse<>().CreateSuccessResponse(resultMap);
        }

        // 解析userLiquidDOS  将里面的数据处理后返回
        // json 不返回
        resultMap = userLiquids.stream().filter(item -> item.getLiquidBeforeTranslation() != null && item.getLiquidAfterTranslation() != null)
                .flatMap(item -> {
                    String before = item.getLiquidBeforeTranslation();
                    String after = item.getLiquidAfterTranslation();
                    if (JsonUtils.isJson(before) || JsonUtils.isJson(after)) {
                        return Stream.empty(); // 跳过该项
                    }

                    return Stream.of(Map.entry(before, after));
                }).collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (v1, v2) -> v1, // 如果 key 重复，保留第一个
                        LinkedHashMap::new
                ));

        return new BaseResponse<>().CreateSuccessResponse(resultMap);
    }

    public BaseResponse<Object> deleteLiquidDataByIds(String shopName, List<Integer> ids) {
        if (ids == null || ids.isEmpty() || shopName == null) {
            return new BaseResponse<>().CreateErrorResponse("Invalid input parameters");
        }

        // 根据ids 逻辑删除数据
        boolean flag = iUserLiquidService.deleteLiquidDataByIds(shopName, ids);
        if (flag) {
            return new BaseResponse<>().CreateSuccessResponse(ids);
        } else {
            return new BaseResponse<>().CreateErrorResponse(null, "Database operation failed");
        }
    }
}
