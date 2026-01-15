package com.bogda.api.logic.BundleApp;

import com.bogda.api.model.controller.response.BaseResponse;
import com.bogda.repository.entity.BundleUserDO;
import com.bogda.repository.repo.bundle.BundleUsersRepo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class BundleUsersService {
    @Autowired
    private BundleUsersRepo bundleUsersRepo;
    public BaseResponse<Object> initUser(String shopName, BundleUserDO bundleUserDO) {
        if (StringUtils.isBlank(shopName) || bundleUserDO == null) {
            return new BaseResponse<>().CreateErrorResponse("Error: shopName or bundleUserDO is null");
        }

        // 获取用户是否存在，存在更新登陆时间， 不存在创建用户
        return null;
    }
}
