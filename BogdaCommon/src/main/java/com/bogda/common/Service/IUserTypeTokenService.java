package com.bogda.common.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.common.entity.DO.UserTypeTokenDO;
import com.bogda.common.model.controller.request.TranslateRequest;
import org.springframework.scheduling.annotation.Async;

public interface IUserTypeTokenService extends IService<UserTypeTokenDO> {

    @Async
    void insertTypeInfo(TranslateRequest request1, int idByShopNameAndTarget);

    Boolean insertTokenInfo(TranslateRequest request1, int idByShopNameAndTarget);
    Integer getStatusByTranslationId(int translationId);

    void updateTokenByTranslationId(int translationId, int tokens, String key);

    void updateStatusByTranslationIdAndStatus(int translationId, int i);

    void insertInitial(String shopName);

}
