package com.bogda.service.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.common.entity.DO.UserTypeTokenDO;
import com.bogda.common.controller.request.TranslateRequest;
import org.springframework.scheduling.annotation.Async;

public interface IUserTypeTokenService extends IService<UserTypeTokenDO> {

    @Async
    void insertTypeInfo(TranslateRequest request1, int idByShopNameAndTarget);

    Boolean insertTokenInfo(TranslateRequest request1, int idByShopNameAndTarget);

    void insertInitial(String shopName);

}
