package com.bogda.service.logic.BundleApp;

import com.bogda.common.controller.response.BaseResponse;
import com.bogda.repository.entity.BundleUserDO;
import com.bogda.repository.repo.bundle.BundleUsersRepo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;

@Component
public class BundleUsersService {
    @Autowired
    private BundleUsersRepo bundleUsersRepo;
    public BaseResponse<Object> initUser(String shopName, BundleUserDO bundleUserDO) {
        if (StringUtils.isBlank(shopName) || bundleUserDO == null) {
            return new BaseResponse<>().CreateErrorResponse("Error: shopName or bundleUserDO is null");
        }
        bundleUserDO.setShopName(shopName);
        // 获取用户是否存在，存在更新登陆时间， 不存在创建用户
        BundleUserDO userByShopName = bundleUsersRepo.getUserByShopName(shopName);
        if (userByShopName != null) {
            bundleUserDO.setUpdatedAt(Timestamp.from(Instant.now()));
            bundleUserDO.setLoginAt(Timestamp.from(Instant.now()));
            bundleUsersRepo.updateUserByShopName(shopName, bundleUserDO);
            return new BaseResponse<>().CreateSuccessResponse(true);
        }else {
            boolean flag = bundleUsersRepo.saveUser(bundleUserDO);
            if (flag) {
                return new BaseResponse<>().CreateSuccessResponse(true);
            }
        }
        return new BaseResponse<>().CreateErrorResponse("Error: save user failed");
    }
}
