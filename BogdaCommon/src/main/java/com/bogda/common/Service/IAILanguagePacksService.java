package com.bogda.common.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.common.entity.DO.AILanguagePacksDO;
import com.bogda.common.model.controller.request.UserLanguageRequest;
import com.bogda.common.model.controller.response.BaseResponse;

public interface IAILanguagePacksService extends IService<AILanguagePacksDO> {


    void addDefaultLanguagePack(String shopName);

    Integer getPackIdByShopName(String shopName);
}
