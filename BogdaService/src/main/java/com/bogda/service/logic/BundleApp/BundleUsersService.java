package com.bogda.service.logic.BundleApp;

import com.bogda.common.controller.response.BaseResponse;
import com.bogda.repository.entity.BundleUserDO;
import com.bogda.repository.entity.BundleUsersDiscountDO;
import com.bogda.repository.repo.bundle.BundleUsersDiscountRepo;
import com.bogda.repository.repo.bundle.BundleUsersRepo;
import com.bogda.repository.repo.bundle.ShopifyDiscountCosmos;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Component
public class BundleUsersService {
    @Autowired
    private BundleUsersRepo bundleUsersRepo;
    @Autowired
    private BundleUsersDiscountRepo bundleUsersDiscountRepo;
    @Autowired
    private ShopifyDiscountCosmos shopifyDiscountCosmos;
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

    /**
     * 用户卸载：1）Bundle_Users.uninstall_at 设为当前 UTC；
     * 2）该 shop 下所有活跃未删折扣 status/is_deleted 改为 false；
     * 3）Cosmos 中对应文档删除。
     */
    public BaseResponse<Object> uninstall(String shopName) {
        if (StringUtils.isBlank(shopName)) {
            return new BaseResponse<>().CreateErrorResponse("Error: shopName is blank");
        }
        bundleUsersRepo.updateUninstallAtByShopName(shopName);
        List<BundleUsersDiscountDO> list = bundleUsersDiscountRepo.listActiveAndNotDeletedByShopName(shopName);
        if (list != null && !list.isEmpty()) {
            bundleUsersDiscountRepo.updateStatusAndIsDeletedForActiveByShopName(shopName, false, false);
            for (BundleUsersDiscountDO d : list) {
                String discountId = d.getDiscountId();
                if (discountId != null) {
                    shopifyDiscountCosmos.deleteByIdAndShopName(discountId, shopName);
                }
            }
        }
        return new BaseResponse<>().CreateSuccessResponse(true);
    }
}
