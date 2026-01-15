package com.bogda.api.logic.BundleApp;

import com.azure.cosmos.models.SqlParameter;
import com.bogda.api.entity.DTO.DiscountBasicDTO;
import com.bogda.api.model.controller.response.BaseResponse;
import com.bogda.repository.container.ShopifyDiscountDO;
import com.bogda.repository.repo.cosmos.ShopifyDiscountRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BundleDiscountService {
    @Autowired
    private ShopifyDiscountRepo shopifyDiscountRepo;

    public BaseResponse<Object> saveUserDiscount(String shopName, ShopifyDiscountDO shopifyDiscountDO) {
        if (shopName == null || shopifyDiscountDO == null) {
            return new BaseResponse<>().CreateErrorResponse("Error: shopName or shopifyDiscountDO is null");
        }

        shopifyDiscountDO.setShopName(shopName);
        if (shopifyDiscountRepo.saveDiscount(shopifyDiscountDO)) {
            return new BaseResponse<>().CreateSuccessResponse(true);
        }
        return new BaseResponse<>().CreateErrorResponse("Error: failed to save discount");
    }

    public BaseResponse<Object> getUserDiscount(String shopName, String discountGid) {
        if (shopName == null || discountGid == null) {
            return new BaseResponse<>().CreateErrorResponse("Error: shopName is null");
        }

        ShopifyDiscountDO shopifyDiscountDO = shopifyDiscountRepo.getDiscountByIdAndShopName(discountGid, shopName);
        if (shopifyDiscountDO != null) {
            return new BaseResponse<>().CreateSuccessResponse(shopifyDiscountDO);
        }
        return new BaseResponse<>().CreateErrorResponse("Error: failed to get discount");
    }

    public BaseResponse<Object> deleteUserDiscount(String shopName, String discountGid) {
        if (shopName == null || discountGid == null) {
            return new BaseResponse<>().CreateErrorResponse("Error: shopName or discountGid is null");
        }

        if (shopifyDiscountRepo.deleteByIdAndShopName(discountGid, shopName)){
            return new BaseResponse<>().CreateSuccessResponse(true);
        }
        return new BaseResponse<>().CreateErrorResponse("Error: failed to delete discount");
    }

    public BaseResponse<Object> batchQueryUserDiscount(String shopName) {
        if (shopName == null) {
            return new BaseResponse<>().CreateErrorResponse("Error: shopName is null");
        }

        String sql = """
                SELECT c.discountGid, c.status, c.discountData.metafields, c.discountData.basic_information
                FROM c WHERE c.shopName = @shopName
                """;

        List<SqlParameter> parameters = List.of(new SqlParameter("@shopName", shopName));
        List<DiscountBasicDTO> data = shopifyDiscountRepo.queryBySql(sql, parameters, shopName, DiscountBasicDTO.class);
        if (data != null) {
            // 将shopName存入data中
            data.forEach(discountBasicDTO -> discountBasicDTO.setShopName(shopName));
            return new BaseResponse<>().CreateSuccessResponse(data);
        }
        return new BaseResponse<>().CreateErrorResponse("Error: failed to get discount");
    }
}
