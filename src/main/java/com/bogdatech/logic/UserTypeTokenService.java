package com.bogdatech.logic;

import com.bogdatech.Service.IUserTypeTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UserTypeTokenService {
    private final IUserTypeTokenService userTypeTokenService;
    @Autowired
    public UserTypeTokenService(IUserTypeTokenService userTypeTokenService) {
        this.userTypeTokenService = userTypeTokenService;
    }

    /**
     * 调用方法获取shopify本地数据存储
     * @param usersDO 用户数据对象，包含用户信息（如名字、邮箱、店铺名等）
     * @return BaseResponse<Object> 通用响应对象，包含操作结果（成功或失败信息）
     */
}
