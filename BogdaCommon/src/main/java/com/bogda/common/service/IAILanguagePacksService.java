package com.bogda.common.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.common.entity.DO.AILanguagePacksDO;

public interface IAILanguagePacksService extends IService<AILanguagePacksDO> {
    void addDefaultLanguagePack(String shopName);

    Integer getPackIdByShopName(String shopName);
}
