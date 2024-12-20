package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.AILanguagePacksDO;
import com.bogdatech.model.controller.request.UserLanguageRequest;
import com.bogdatech.model.controller.response.BaseResponse;

public interface IAILanguagePacksService extends IService<AILanguagePacksDO> {

    BaseResponse<Object> readAILanguagePacks();

    void addDefaultLanguagePack(String shopName);

    BaseResponse<Object> changeLanguagePack(UserLanguageRequest userLanguageRequest);

    AILanguagePacksDO getPromotByPackId(Integer packId);

    Integer getPackIdByShopName(String shopName);
}
