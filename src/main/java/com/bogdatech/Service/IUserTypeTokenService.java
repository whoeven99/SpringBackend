package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.UserTypeTokenDO;
import com.bogdatech.model.controller.request.TranslateRequest;
import org.springframework.scheduling.annotation.Async;

public interface IUserTypeTokenService extends IService<UserTypeTokenDO> {

    @Async
    void insertTypeInfo(TranslateRequest request1, int idByShopNameAndTarget);

    int getStatusByTranslationId(int translationId);
}
