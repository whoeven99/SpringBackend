package com.bogda.service.logic.BundleApp;

import com.azure.cosmos.models.SqlParameter;
import com.bogda.api.entity.DTO.DiscountBasicDTO;
import com.bogda.common.controller.response.BaseResponse;
import com.bogda.repository.container.ShopifyDiscountDO;
import com.bogda.repository.repo.bundle.BundleUsersDiscountRepo;
import com.bogda.repository.repo.bundle.ShopifyDiscountCosmos;
import kotlin.Pair;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BundleDiscountService {
    @Autowired
    private ShopifyDiscountCosmos shopifyDiscountCosmos;
    @Autowired
    private BundleUsersDiscountRepo bundleUsersDiscountRepo;
    private static final String DISCOUNT_ID = "gid://shopify/DiscountAutomaticNode/";

    public BaseResponse<Object> saveUserDiscount(String shopName, ShopifyDiscountDO shopifyDiscountDO) {
        if (shopName == null || shopifyDiscountDO == null || StringUtils.isBlank(shopName) || StringUtils.isBlank(shopifyDiscountDO.getDiscountGid())) {
            return new BaseResponse<>().CreateErrorResponse("Error: shopName or shopifyDiscountDO is null");
        }
        String offerName = shopifyDiscountDO.getDiscountData().getBasicInformation().getOfferName();
        if (offerName == null){
            return new BaseResponse<>().CreateErrorResponse("Error: offerName is null");
        }

        shopifyDiscountDO.setShopName(shopName);
        String replaceId = shopifyDiscountDO.getDiscountGid().replace(DISCOUNT_ID, "");
        shopifyDiscountDO.setId(replaceId);
        boolean flag1 = shopifyDiscountCosmos.saveDiscount(shopifyDiscountDO);
        boolean flag2 = bundleUsersDiscountRepo.insertUserDiscount(shopName, replaceId, offerName);

        if (flag1 && flag2) {
            return new BaseResponse<>().CreateSuccessResponse(true);
        }
        return new BaseResponse<>().CreateErrorResponse("Error: failed to save discount");
    }

    public BaseResponse<Object> getUserDiscount(String shopName, String discountGid) {
        if (shopName == null || discountGid == null || StringUtils.isBlank(shopName) || StringUtils.isBlank(discountGid)) {
            return new BaseResponse<>().CreateErrorResponse("Error: shopName is null");
        }
        discountGid = discountGid.replace(DISCOUNT_ID, "");
        ShopifyDiscountDO shopifyDiscountDO = shopifyDiscountCosmos.getDiscountByIdAndShopName(discountGid, shopName);
        if (shopifyDiscountDO != null) {
            return new BaseResponse<>().CreateSuccessResponse(shopifyDiscountDO);
        }
        return new BaseResponse<>().CreateErrorResponse("Error: failed to get discount");
    }

    public BaseResponse<Object> deleteUserDiscount(String shopName, String discountGid) {
        if (shopName == null || discountGid == null || StringUtils.isBlank(shopName) || StringUtils.isBlank(discountGid)) {
            return new BaseResponse<>().CreateErrorResponse("Error: shopName or discountGid is null");
        }
        String updateDiscountGid = discountGid.replace(DISCOUNT_ID, "");
        if (shopifyDiscountCosmos.deleteByIdAndShopName(updateDiscountGid, shopName)){
            return new BaseResponse<>().CreateSuccessResponse(discountGid);
        }
        return new BaseResponse<>().CreateErrorResponse("Error: failed to delete discount");
    }

    public BaseResponse<Object> batchQueryUserDiscount(String shopName) {
        if (shopName == null || StringUtils.isBlank(shopName)) {
            return new BaseResponse<>().CreateErrorResponse("Error: shopName is null");
        }

        String sql = """
                SELECT c.discountGid, c.status, c.discountData.metafields, c.discountData.basic_information
                FROM c WHERE c.shopName = @shopName
                """;

        List<SqlParameter> parameters = List.of(new SqlParameter("@shopName", shopName));
        List<DiscountBasicDTO> data = shopifyDiscountCosmos.queryBySql(sql, parameters, shopName, DiscountBasicDTO.class);
        if (data != null) {
            // 将shopName存入data中
            data.forEach(discountBasicDTO -> discountBasicDTO.setShopName(shopName));
            return new BaseResponse<>().CreateSuccessResponse(data);
        }
        return new BaseResponse<>().CreateErrorResponse("Error: failed to get discount");
    }

    public BaseResponse<Object> updateUserDiscount(String shopName, ShopifyDiscountDO shopifyDiscountDO) {
        if (shopName == null || shopifyDiscountDO == null || StringUtils.isBlank(shopName) || StringUtils.isBlank(shopifyDiscountDO.getDiscountGid())) {
            return new BaseResponse<>().CreateErrorResponse("Error: shopName or shopifyDiscountDO is null");
        }

        shopifyDiscountDO.setShopName(shopName);
        String updateDiscountGid = shopifyDiscountDO.getDiscountGid().replace(DISCOUNT_ID, "");
        if (shopifyDiscountCosmos.updateDiscount(updateDiscountGid, shopName, shopifyDiscountDO.getDiscountData())) {
            return new BaseResponse<>().CreateSuccessResponse(true);
        }
        return new BaseResponse<>().CreateErrorResponse("Error: failed to update discount");
    }

    public BaseResponse<Object> updateUserDiscountStatus(String shopName, String discountGid, String status) {
        if (shopName == null || discountGid == null || status == null || StringUtils.isBlank(shopName) || StringUtils.isBlank(discountGid)) {
            return new BaseResponse<>().CreateErrorResponse("Error: shopName or discountGid or status is null");
        }

        String updateDiscountGid = discountGid.replace(DISCOUNT_ID, "");
        if (shopifyDiscountCosmos.updateDiscountStatus(updateDiscountGid, shopName, status)) {
            return new BaseResponse<>().CreateSuccessResponse(new Pair<String, String>(discountGid, status));
        }
        return new BaseResponse<>().CreateErrorResponse("Error: failed to update discount status");
    }
}
