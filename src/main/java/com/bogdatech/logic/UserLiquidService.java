package com.bogdatech.logic;

import com.bogdatech.Service.IUserLiquidService;
import com.bogdatech.entity.DO.UserLiquidDO;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.utils.JsonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        UserLiquidDO resultData;
        boolean isSuccess;

        // 判断有没有传 id ， 没有，就存； 有的话，就更新
        // 查询 id 是否存在
        if (userLiquidDO.getId() == null) {
            // 如果id为空，就写入
            isSuccess = iUserLiquidService.save(userLiquidDO);
            resultData = isSuccess
                    ? iUserLiquidService.getLiquidData(shopName, userLiquidDO.getLiquidAfterTranslation(), userLiquidDO.getLanguageCode(), userLiquidDO.getLiquidBeforeTranslation())
                    : null;

        } else {
            // 查询是否存在
            UserLiquidDO existing = iUserLiquidService.getLiquidData(
                    shopName,
                    userLiquidDO.getLiquidAfterTranslation(),
                    userLiquidDO.getLanguageCode(),
                    userLiquidDO.getLiquidBeforeTranslation()
            );

            if (existing != null) {
                return new BaseResponse<>().CreateErrorResponse("Liquid data already exists");
            }

            isSuccess = iUserLiquidService.updateLiquidDataById(userLiquidDO);
            resultData = isSuccess ? userLiquidDO : null;
        }
        if (resultData != null) {
            resultData.setUpdatedAt(null);
            resultData.setCreatedAt(null);
        }

        if (isSuccess) {
            return new BaseResponse<>().CreateSuccessResponse(resultData);
        }
        return new BaseResponse<>().CreateErrorResponse(null, "Database operation failed");
    }

    public BaseResponse<Object> parseLiquidDataByShopNameAndLanguage(String shopName, String languageCode) {
        List<UserLiquidDO> userLiquids = iUserLiquidService.selectLiquidDataByShopNameAndLanguageCode(shopName, languageCode);
        if (userLiquids == null || userLiquids.isEmpty()) {
            return new BaseResponse<>().CreateErrorResponse("No data found");
        }

        // 解析userLiquidDOS  将里面的数据处理后返回
        // json 不返回
        Map<String, List<Object>> resultMap = userLiquids.stream()
                .filter(item -> item.getLiquidBeforeTranslation() != null
                        && item.getLiquidAfterTranslation() != null)
                .flatMap(item -> {
                    String before = item.getLiquidBeforeTranslation();
                    String after = item.getLiquidAfterTranslation();
                    Boolean replacementMethod = item.getReplacementMethod();

                    if (JsonUtils.isJson(before) || JsonUtils.isJson(after)) {
                        return Stream.empty(); // 跳过该项
                    }

                    Map.Entry<String, List<Object>> entry =
                            Map.entry(before, List.of(after, replacementMethod));

                    return Stream.of(entry);
                })
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (v1, v2) -> v1,
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

    public BaseResponse<Object> updateLiquidReplacementMethod(String shopName, Integer id) {
        if (shopName == null || id == null) {
            return new BaseResponse<>().CreateErrorResponse("Invalid input parameters");
        }

        // 获取当前replacementMethod的数据，然后修改
        UserLiquidDO userLiquidDO = iUserLiquidService.getById(id);
        if (userLiquidDO == null) {
            return new BaseResponse<>().CreateErrorResponse("Data not found");
        }

        boolean flag = iUserLiquidService.updateReplacementMethodById(id, !userLiquidDO.getReplacementMethod());
        if (flag) {
            return new BaseResponse<>().CreateSuccessResponse(!userLiquidDO.getReplacementMethod());
        }
        return new BaseResponse<>().CreateErrorResponse(null, "Database operation failed");
    }
}
