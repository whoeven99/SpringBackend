package com.bogdatech.logic;

import com.bogdatech.Service.IUserLiquidService;
import com.bogdatech.entity.DO.UserLiquidDO;
import com.bogdatech.entity.VO.InsertLiquidVO;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
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

        // 分组：按 shopName + liquidBeforeTranslation 分组
        List<Map<String, String>> result = userLiquidList.stream()
                .collect(Collectors.groupingBy(
                        item -> item.getShopName() + "_" + item.getLiquidBeforeTranslation()
                ))
                .values().stream()
                .map(list -> {
                    UserLiquidDO first = list.get(0);
                    Map<String, String> targetJson = list.stream()
                            .collect(Collectors.toMap(
                                    UserLiquidDO::getLanguageCode,
                                    UserLiquidDO::getLiquidAfterTranslation
                            ));
                    Map<String, String> map = new HashMap<>();
                    map.put("shop", first.getShopName());
                    map.put("sourceText", first.getLiquidBeforeTranslation());
                    map.put("targetJson", JsonUtils.objectToJson(targetJson));
                    return map;
                })
                .toList();

        return new BaseResponse<>().CreateSuccessResponse(result);
    }

    public BaseResponse<Object> insertShopNameLiquidData(String shopName, InsertLiquidVO insertLiquidVO) {
        // 解析targetJson数据
        Map<String, String> liquidResponse = JsonUtils.jsonToObjectWithNull(insertLiquidVO.getTargetJson(), new TypeReference<Map<String, String>>() {
        });
        if (liquidResponse == null) {
            return new BaseResponse<>().CreateErrorResponse("targetJson is null");
        }

        List<Boolean> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : liquidResponse.entrySet()) {
            // 判断这个值在数据库中是否存在， 不存在插入；存在，更新
            UserLiquidDO liquidData = iUserLiquidService.getLiquidData(shopName, entry.getKey(), insertLiquidVO.getSourceText());
            UserLiquidDO userLiquidDO = new UserLiquidDO();
            userLiquidDO.setShopName(shopName);
            userLiquidDO.setLiquidBeforeTranslation(insertLiquidVO.getSourceText());
            userLiquidDO.setLiquidAfterTranslation(entry.getValue());
            userLiquidDO.setLanguageCode(entry.getKey());
            if (liquidData == null) {
                boolean save = iUserLiquidService.save(userLiquidDO);
                result.add(save);
            } else {
                boolean updateFlag = iUserLiquidService.updateLiquidData(shopName, userLiquidDO);
                result.add(updateFlag);
            }
        }
        return new BaseResponse<>().CreateSuccessResponse(result);
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
}
