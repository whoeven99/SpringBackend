package com.bogda.service.logic.BundleApp;

import com.alibaba.fastjson.JSONObject;
import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.entity.VO.BundleInitialVO;
import com.bogda.common.utils.AppInsightsUtils;
import com.bogda.common.utils.JsonUtils;
import com.bogda.common.utils.ShopifyRequestUtils;
import com.bogda.integration.model.ShopifyCreateStorefrontAccessTokenResponse;
import com.bogda.integration.shopify.ShopifyHttpIntegration;
import com.bogda.repository.entity.BundleUserDO;
import com.bogda.repository.entity.BundleUsersDiscountDO;
import com.bogda.repository.repo.bundle.BundleUsersDiscountRepo;
import com.bogda.repository.repo.bundle.BundleUsersRepo;
import com.bogda.repository.repo.bundle.ShopifyDiscountCosmos;
import kotlin.Pair;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class BundleUsersService {
    @Autowired
    private BundleUsersRepo bundleUsersRepo;
    @Autowired
    private BundleUsersDiscountRepo bundleUsersDiscountRepo;
    @Autowired
    private ShopifyDiscountCosmos shopifyDiscountCosmos;
    @Autowired
    private ShopifyHttpIntegration shopifyHttpIntegration;
    @Autowired
    private BundleUserAsyncService bundleUserAsyncService;


    /**
     * 初始化用户：1）创建/复用 storefrontAccessToken 并落库；2）立即返回前端；3）异步拉取 shop 主信息并更新 user_tag/first_name/last_name/email。
     */
    public BaseResponse<Object> initUser(String shopName, BundleUserDO bundleUserDO) {
        if (StringUtils.isBlank(shopName) || bundleUserDO == null) {
            return new BaseResponse<>().CreateErrorResponse("Error: shopName or bundleUserDO is null");
        }
        String accessToken = bundleUserDO.getAccessToken();
        if (StringUtils.isBlank(accessToken)) {
            return new BaseResponse<>().CreateErrorResponse("Error: accessToken is required");
        }
        bundleUserDO.setShopName(shopName);
        Timestamp now = Timestamp.from(Instant.now());
        BundleUserDO existing = bundleUsersRepo.getUserByShopName(shopName);

        String storefrontToken;
        if (existing != null && StringUtils.isNotBlank(existing.getStorefrontAccessToken())) {
            storefrontToken = existing.getStorefrontAccessToken();
            bundleUserDO.setUpdatedAt(now);
            bundleUserDO.setLoginAt(now);
            bundleUsersRepo.updateUserByShopName(shopName, bundleUserDO);
            return new BaseResponse<>().CreateSuccessResponse(new BundleInitialVO(storefrontToken));
        }

        Pair<String, String> storefrontAccessToken = createStorefrontAccessToken(shopName, accessToken);
        if (storefrontAccessToken == null) {
            return new BaseResponse<>().CreateErrorResponse("Error: storefrontAccessToken create failed");
        }

        storefrontToken = storefrontAccessToken.getFirst();
        String storefrontId = storefrontAccessToken.getSecond();
        if (StringUtils.isBlank(storefrontToken) || StringUtils.isBlank(storefrontId)) {
            return new BaseResponse<>().CreateErrorResponse("Error: storefrontAccessToken create failed");
        }

        bundleUserDO.setStorefrontAccessToken(storefrontToken);
        bundleUserDO.setStorefrontId(storefrontId);
        bundleUserDO.setUpdatedAt(now);
        bundleUserDO.setLoginAt(now);

        // 异步拉取 shop 主信息并更新 user_tag/first_name/last_name/email
        bundleUserAsyncService.createOrUpdateUser(existing, bundleUserDO);
        return new BaseResponse<>().CreateSuccessResponse(new BundleInitialVO(storefrontToken));
    }

    /**
     * 用户卸载：1）Bundle_Users.uninstall_at 设为当前 UTC；删除BundleUserDO 里的storefront_access_token 和storefront_id
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
            bundleUsersDiscountRepo.updateStatusAndIsDeletedForActiveByShopName(shopName, false, true);
            for (BundleUsersDiscountDO d : list) {
                String discountId = d.getDiscountId();
                if (discountId != null) {
                    shopifyDiscountCosmos.deleteByIdAndShopName(discountId, shopName);
                }
            }
        }
        return new BaseResponse<>().CreateSuccessResponse(true);
    }

    /**
     * 创建 Storefront Access Token（Admin GraphQL）。
     * 参考：https://shopify.dev/docs/api/admin-graphql/2025-10/mutations/storefrontAccessTokenCreate
     * first: StorefrontAccessToken  second: StorefrontId
     *
     * @return 新创建的 storefront accessToken 字符串，失败或 userErrors 时返回 null
     */
    public Pair<String, String> createStorefrontAccessToken(String shopName, String adminAccessToken) {
        Map<String, Object> variables = Map.of(
                "input", Map.of("title", shopName)
        );
        String response = shopifyHttpIntegration.sendShopifyPost(shopName, adminAccessToken, ShopifyRequestUtils.createAccessTokenQuery(), variables);

        if (response == null) {
            return null;
        }

        ShopifyCreateStorefrontAccessTokenResponse responseDo = null;

        try {
            responseDo = JsonUtils.OBJECT_MAPPER.readValue(response, ShopifyCreateStorefrontAccessTokenResponse.class);
            return new Pair<>(responseDo.getData().getStorefrontAccessTokenCreate().getStorefrontAccessToken().getAccessToken()
                    , responseDo.getData().getStorefrontAccessTokenCreate().getShop().getId());
        } catch (Exception e) {
            AppInsightsUtils.trackTrace("FatalException in createStorefrontAccessToken : " + e.getMessage());
            return null;
        }
    }


}
