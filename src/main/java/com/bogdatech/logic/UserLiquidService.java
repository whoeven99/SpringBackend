package com.bogdatech.logic;

import com.bogdatech.Service.IUserLiquidService;
import com.bogdatech.entity.DO.UserLiquidDO;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.utils.JsonUtils;
import com.bogdatech.utils.JsoupUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
        List<UserLiquidDO> userLiquids = iUserLiquidService.selectLiquidData(shopName, language, null);
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

                    boolean isHtml = JsoupUtils.isHtml(before) || JsoupUtils.isHtml(after);

                    if (isHtml) {
                        List<String> beforeTexts = JsoupUtils.extractTextFromHtml(before);
                        List<String> afterTexts = JsoupUtils.extractTextFromHtml(after);

                        int size = Math.min(beforeTexts.size(), afterTexts.size());

                        // 将 index 映射成 key-value 对
                        return IntStream.range(0, size)
                                .mapToObj(i -> Map.entry(beforeTexts.get(i), afterTexts.get(i)));
                    } else {
                        return Stream.of(Map.entry(before, after));
                    }
                }).collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (v1, v2) -> v1, // 如果 key 重复，保留第一个
                        LinkedHashMap::new
                ));

        return new BaseResponse<>().CreateSuccessResponse(resultMap);
    }
}
