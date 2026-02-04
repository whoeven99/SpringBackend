package com.bogda.service.logic.BundleApp;

import com.azure.cosmos.models.SqlParameter;
import com.bogda.api.entity.DTO.DiscountBasicDTO;
import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.entity.DTO.BundleDiscountDTO;
import com.bogda.common.entity.DTO.BundleDiscountAmountReportDTO;
import com.bogda.common.entity.VO.BundleDisplayDataVO;
import com.bogda.common.entity.VO.BundleDiscountAmountReportVO;
import com.bogda.repository.container.ShopifyDiscountDO;
import com.bogda.repository.entity.BundleUsersDiscountDO;
import com.bogda.repository.repo.bundle.BundleUsersDiscountRepo;
import com.bogda.repository.repo.bundle.ShopifyDiscountCosmos;
import com.bogda.service.logic.RateDataService;
import com.bogda.service.logic.bundle.redis.BundleBudgetRedisService;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class BundleDiscountService {
    @Autowired
    private ShopifyDiscountCosmos shopifyDiscountCosmos;
    @Autowired
    private BundleUsersDiscountRepo bundleUsersDiscountRepo;
    @Autowired
    private BundleBudgetRedisService bundleBudgetRedisService;
    @Autowired
    private RateDataService rateDataService;

    private static final String DISCOUNT_ID = "gid://shopify/DiscountAutomaticNode/";

    public BaseResponse<Object> saveUserDiscount(String shopName, ShopifyDiscountDO shopifyDiscountDO) {
        String offerName = shopifyDiscountDO.getDiscountData().getBasicInformation().getOfferName();
        if (offerName == null) {
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
        String updateDiscountGid = discountGid.replace(DISCOUNT_ID, "");
        // 修改db表里面的数据
        bundleUsersDiscountRepo.updateDiscountDelete(shopName, updateDiscountGid, true);

        if (shopifyDiscountCosmos.deleteByIdAndShopName(updateDiscountGid, shopName)) {
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
        boolean status = "ACTIVE".equals(shopifyDiscountDO.getStatus());
        bundleUsersDiscountRepo.updateDiscountStatus(shopName, shopifyDiscountDO.getDiscountGid(), status);
        if (shopifyDiscountCosmos.updateDiscount(updateDiscountGid, shopName, shopifyDiscountDO.getDiscountData())) {
            return new BaseResponse<>().CreateSuccessResponse(true);
        }
        return new BaseResponse<>().CreateErrorResponse("Error: failed to update discount");
    }

    public BaseResponse<Object> updateUserDiscountStatus(String shopName, String discountGid, String status) {
        String updateDiscountGid = discountGid.replace(DISCOUNT_ID, "");

        // 修改db表里面的数据
        boolean statusFlag = "ACTIVE".equals(status);
        bundleUsersDiscountRepo.updateDiscountStatus(shopName, updateDiscountGid, statusFlag);

        if (shopifyDiscountCosmos.updateDiscountStatus(updateDiscountGid, shopName, status)) {
            return new BaseResponse<>().CreateSuccessResponse(new Pair<String, String>(discountGid, status));
        }
        return new BaseResponse<>().CreateErrorResponse("Error: failed to update discount status");
    }

    /**
     * 下单优惠金额上报：Redis 原子累加 usedDailyBudget/usedTotalBudget，熔断则立即 disable 并写回 Cosmos。
     * <p>Redis 为准，Cosmos 异步投影（尽量减少 RU）。</p>
     */
    public BaseResponse<Object> reportDiscountAmount(String shopName, BundleDiscountAmountReportDTO dto) {
        if (dto.getDiscountAmount() < 0) {
            return new BaseResponse<>().CreateErrorResponse("Error: discountAmount must be >= 0");
        }

        String discountName = dto.getDiscountName();

        // 根据 discountName和shopName获取discountGid数据
        BundleUsersDiscountDO discountData = bundleUsersDiscountRepo.getAllByShopNameAndDiscountName(shopName, discountName);
        if (discountData == null) {
            return new BaseResponse<>().CreateErrorResponse("Error: discountName not exist");
        }

        // 将amount 根据 currentCode 转为 USD，然后存进redis中
        double rate = rateDataService.getRateByRateMap(dto.getCurrencyCode(), "USD");
        double realAmount = dto.getDiscountAmount() * rate;
        String discountId = discountData.getDiscountId();

        // first返回每日使用数据， second返回所有使用数据
        Pair<Double, Double> afterAddData = bundleBudgetRedisService.addUsedAmount(shopName, discountData.getDiscountId(), realAmount);

        if (afterAddData == null) {
            return new BaseResponse<>().CreateErrorResponse("Error: add used amount failed");
        }

        // 读取预算阈值（dailyBudget/totalBudget）以 Cosmos 为准
        ShopifyDiscountDO discount = shopifyDiscountCosmos.getDiscountByIdAndShopName(discountId, shopName);
        if (discount == null || discount.getDiscountData() == null
                || discount.getDiscountData().getTargetingSettings() == null
                || discount.getDiscountData().getTargetingSettings().getBudget() == null
                || discount.getDiscountData().getBasicInformation() == null) {
            return new BaseResponse<>().CreateSuccessResponse("Error: discount not exist");
        }

        ShopifyDiscountDO.DiscountData.TargetingSettings.Budget budget = discount.getDiscountData().getTargetingSettings().getBudget();
        Double dailyBudget = budget.getDailyBudget();
        Double totalBudget = budget.getTotalBudget();
        Double usedDailyBudget = afterAddData.getFirst();
        Double usedTotalBudget = afterAddData.getSecond();


        boolean circuitOpen = (dailyBudget != null && usedDailyBudget >= dailyBudget)
                || (totalBudget != null && usedTotalBudget >= totalBudget);

        if (circuitOpen) {
            // 触发熔断：立即 disable，并把 used 值写回 Cosmos（局部 patch，减少 RU）
            boolean ok = shopifyDiscountCosmos.patchBudgetAndEnable(discountName, shopName,
                    usedDailyBudget, usedTotalBudget, false);
            Boolean enable = ok ? Boolean.FALSE : null;
            return new BaseResponse<>().CreateSuccessResponse(new BundleDiscountAmountReportVO(true, enable,
                    usedDailyBudget, usedTotalBudget));
        }

        // 未熔断：异步回写 used 值（预算热数据以 Redis 为准）
        asyncPatchBudgetUsedOnly(discountName, shopName, usedDailyBudget, usedTotalBudget);
        return new BaseResponse<>().CreateSuccessResponse(new BundleDiscountAmountReportVO(false, true,
                usedDailyBudget, usedTotalBudget));
    }

    @Async
    public void asyncPatchBudgetUsedOnly(String discountId, String shopName, double usedDaily, double usedTotal) {
        shopifyDiscountCosmos.patchBudgetUsedOnly(discountId, shopName, usedDaily, usedTotal);
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
            bundleDisplayDataVO.setDiscountId(DISCOUNT_ID + bd.getDiscountId());
            bundleDisplayDataVO.setGmv(bd.getGmv());
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
        return new BaseResponse<>().CreateSuccessResponse(new BundleDiscountDTO(discountList));
    }
}
