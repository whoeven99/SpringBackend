package com.bogda.service.logic.BundleApp;

import com.azure.cosmos.models.SqlParameter;
import com.bogda.api.entity.DTO.DiscountBasicDTO;
import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.entity.VO.BundleDisplayDataVO;
import com.bogda.repository.container.ShopifyDiscountDO;
import com.bogda.repository.entity.BundleUsersDiscountDO;
import com.bogda.repository.repo.bundle.BundleUsersDiscountRepo;
import com.bogda.repository.repo.bundle.ShopifyDiscountCosmos;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class BundleDiscountService {
    @Autowired
    private ShopifyDiscountCosmos shopifyDiscountCosmos;
    @Autowired
    private BundleUsersDiscountRepo bundleUsersDiscountRepo;

    private static final String DISCOUNT_ID = "gid://shopify/DiscountAutomaticNode/";

    public BaseResponse<Object> saveUserDiscount(String shopName, ShopifyDiscountDO shopifyDiscountDO) {
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
        discountGid = discountGid.replace(DISCOUNT_ID, "");
        ShopifyDiscountDO shopifyDiscountDO = shopifyDiscountCosmos.getDiscountByIdAndShopName(discountGid, shopName);
        if (shopifyDiscountDO != null) {
            return new BaseResponse<>().CreateSuccessResponse(shopifyDiscountDO);
        }
        return new BaseResponse<>().CreateErrorResponse("Error: failed to get discount");
    }

    public BaseResponse<Object> deleteUserDiscount(String shopName, String discountGid) {

        // 修改db表里面的数据
        bundleUsersDiscountRepo.updateDiscountDelete(shopName, discountGid, true);
        String updateDiscountGid = discountGid.replace(DISCOUNT_ID, "");
        if (shopifyDiscountCosmos.deleteByIdAndShopName(updateDiscountGid, shopName)){
            return new BaseResponse<>().CreateSuccessResponse(discountGid);
        }
        return new BaseResponse<>().CreateErrorResponse("Error: failed to delete discount");
    }

    public BaseResponse<Object> batchQueryUserDiscount(String shopName) {

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

        shopifyDiscountDO.setShopName(shopName);
        String updateDiscountGid = shopifyDiscountDO.getDiscountGid().replace(DISCOUNT_ID, "");

        // 修改db表里面的数据
        bundleUsersDiscountRepo.updateDiscountStatus(shopName, shopifyDiscountDO.getDiscountGid(), shopifyDiscountDO.getStatus());
        if (shopifyDiscountCosmos.updateDiscount(updateDiscountGid, shopName, shopifyDiscountDO.getDiscountData())) {
            return new BaseResponse<>().CreateSuccessResponse(true);
        }
        return new BaseResponse<>().CreateErrorResponse("Error: failed to update discount");
    }

    public BaseResponse<Object> updateUserDiscountStatus(String shopName, String discountGid, String status) {
        // 修改db表里面的数据
        bundleUsersDiscountRepo.updateDiscountStatus(shopName, discountGid, status);
        String updateDiscountGid = discountGid.replace(DISCOUNT_ID, "");
        if (shopifyDiscountCosmos.updateDiscountStatus(updateDiscountGid, shopName, status)) {
            return new BaseResponse<>().CreateSuccessResponse(new Pair<String, String>(discountGid, status));
        }
        return new BaseResponse<>().CreateErrorResponse("Error: failed to update discount status");
    }

    public BaseResponse<Object> getActiveOffersByUser(String shopName) {

        int countByShopName = bundleUsersDiscountRepo.getCountByShopName(shopName);
        return new BaseResponse<>().CreateSuccessResponse(countByShopName);
    }

    // 获取用户折扣所有数据
    public BaseResponse<Object> getAllUserDiscount(String shopName) {

        List<BundleUsersDiscountDO> allByShopName = bundleUsersDiscountRepo.getAllByShopName(shopName);

        // 对allByShopName做处理
        if (allByShopName == null || allByShopName.isEmpty()) {
            return new BaseResponse<>().CreateSuccessResponse(new ArrayList<>());
        }

        List<BundleDisplayDataVO> discountList = new ArrayList<>();
        allByShopName.forEach(bd -> {
            BundleDisplayDataVO bundleDisplayDataVO = new BundleDisplayDataVO();
            bundleDisplayDataVO.setShopName(bd.getShopName());
            bundleDisplayDataVO.setDiscountId(bd.getDiscountId());
            bundleDisplayDataVO.setStatus(bd.getStatus());
            bundleDisplayDataVO.setGmv(bd.getGmv());
            bundleDisplayDataVO.setDiscountName(bd.getDiscountName());
            bundleDisplayDataVO.setExposurePv(bd.getExposurePv());
            bundleDisplayDataVO.setAddToCartPv(bd.getAddToCartPv());

            // 计算conversion  下单pv / 曝光pv
            double conversion = 0;
            if (bd.getCheckoutStartedPv() != 0) {
                conversion = (double) bd.getCheckoutStartedPv() / bd.getExposurePv();
            }

            bundleDisplayDataVO.setConversion(conversion);

            discountList.add(bundleDisplayDataVO);
        });
        return new BaseResponse<>().CreateSuccessResponse(discountList);
    }

    public BaseResponse<Object> getTotalGMV(String shopName) {
        Double totalGmv = bundleUsersDiscountRepo.getAllGmvByShopName(shopName);
        return new BaseResponse<>().CreateSuccessResponse(totalGmv);
    }

    public BaseResponse<Object> getAvgConversion(String shopName) {
        Double avgConversion = bundleUsersDiscountRepo.getAvgConversionByShopName(shopName);
        return new BaseResponse<>().CreateSuccessResponse(avgConversion);
    }
}
