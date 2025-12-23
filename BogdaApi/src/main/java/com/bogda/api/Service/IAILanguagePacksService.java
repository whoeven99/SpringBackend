package com.bogda.api.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.api.entity.DO.AILanguagePacksDO;
import com.bogda.api.model.controller.request.UserLanguageRequest;
import com.bogda.api.model.controller.response.BaseResponse;

public interface IAILanguagePacksService extends IService<AILanguagePacksDO> {


    void addDefaultLanguagePack(String shopName);

    Integer getPackIdByShopName(String shopName);
}
