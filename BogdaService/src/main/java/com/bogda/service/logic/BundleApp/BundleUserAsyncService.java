package com.bogda.service.logic.BundleApp;

import com.bogda.common.utils.AppInsightsUtils;
import com.bogda.common.utils.JsonUtils;
import com.bogda.common.utils.ShopifyRequestUtils;
import com.bogda.integration.model.ShopifyUserResponse;
import com.bogda.integration.shopify.ShopifyHttpIntegration;
import com.bogda.repository.entity.BundleUserDO;
import com.bogda.repository.repo.bundle.BundleUsersRepo;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class BundleUserAsyncService {
    @Autowired
    private BundleUsersRepo bundleUsersRepo;
    @Autowired
    private ShopifyHttpIntegration shopifyHttpIntegration;


    // 异步创建和更新用户数据
    @Async
    public void createOrUpdateUser(BundleUserDO existing, BundleUserDO bundleUserDO) {
        if (existing != null) {
            bundleUsersRepo.updateUserByShopName(bundleUserDO.getShopName(), bundleUserDO);
        } else {
            BundleUserDO updateBundle = queryShopInfo(bundleUserDO.getShopName(), bundleUserDO.getAccessToken(), bundleUserDO);
            bundleUsersRepo.saveUser(updateBundle);
        }

    }

    /**
     * 查询 shop { shopOwnerName, email }
     */
    public BundleUserDO queryShopInfo(String shopName, String adminAccessToken, BundleUserDO bundleUserDO) {
        String response = shopifyHttpIntegration.sendShopifyPost(shopName, adminAccessToken, ShopifyRequestUtils.queryShopOwner(), null);

        try {
            ShopifyUserResponse shopifyUserResponse = JsonUtils.OBJECT_MAPPER.readValue(response, ShopifyUserResponse.class);
            if (shopifyUserResponse == null || shopifyUserResponse.getData() == null) {
                return null;
            }

            String shopOwnerName = shopifyUserResponse.getData().getShop().getShopOwnerName();
            String email = shopifyUserResponse.getData().getShop().getEmail();
            String firstName = null;
            String lastName = null;
            if (StringUtils.isNotBlank(shopOwnerName)) {
                int space = shopOwnerName.indexOf(' ');
                if (space <= 0) {
                    firstName = shopOwnerName;
                } else {
                    firstName = shopOwnerName.substring(0, space).trim();
                    lastName = shopOwnerName.substring(space + 1).trim();
                    if (StringUtils.isBlank(lastName)) {
                        lastName = null;
                    }
                }
            }
            bundleUserDO.setUserTag(shopOwnerName);
            bundleUserDO.setEmail(email);
            bundleUserDO.setFirstName(firstName);
            bundleUserDO.setLastName(lastName);
            return bundleUserDO;
        } catch (JsonProcessingException e) {
            AppInsightsUtils.trackTrace("FatalException in queryShopInfo : " + e.getMessage());
            return null;
        }
    }
}
