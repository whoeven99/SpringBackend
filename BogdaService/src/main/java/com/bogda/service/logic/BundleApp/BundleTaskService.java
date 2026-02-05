package com.bogda.service.logic.BundleApp;

import com.bogda.common.utils.AliyunLogSqlUtils;
import com.bogda.integration.aimodel.AliyunSlsIntegration;
import com.bogda.repository.container.ShopifyDiscountDO;
import com.bogda.repository.entity.BundleUsersDiscountDO;
import com.bogda.repository.repo.bundle.BundleUsersDiscountRepo;
import com.bogda.repository.repo.bundle.BundleUsersRepo;
import com.bogda.repository.repo.bundle.ShopifyDiscountCosmos;
import com.bogda.service.logic.RateDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

@Component
public class BundleTaskService {

    @Autowired
    private BundleUsersRepo bundleUsersRepo;
    @Autowired
    private BundleUsersDiscountRepo bundleUsersDiscountRepo;
    @Autowired
    private ShopifyDiscountCosmos shopifyDiscountCosmos;
    @Autowired
    private AliyunSlsIntegration aliyunSlsIntegration;
    @Autowired
    private RateDataService rateDataService;

    /**
     * 每天 UTC 0 点执行：遍历所有活跃且未删除的 Bundle_Users_Discount，重置 usedDailyBudget = 0，
     * 若 enable == false 且 usedTotalBudget < totalBudget 则恢复 enable = true 并同步至 Cosmos。
     */
    public void resetDailyBudgetAndRecoverEnable() {
        List<BundleUsersDiscountDO> list = bundleUsersDiscountRepo.listActiveAndNotDeleted();
        if (list == null || list.isEmpty()) {
            return;
        }
        for (BundleUsersDiscountDO row : list) {
            String shopName = row.getShopName();
            String discountId = row.getDiscountId();
            if (shopName == null || discountId == null) {
                continue;
            }
            ShopifyDiscountDO doc = shopifyDiscountCosmos.getDiscountByIdAndShopName(discountId, shopName);
            if (doc == null || doc.getDiscountData() == null
                    || doc.getDiscountData().getBasicInformation() == null
                    || doc.getDiscountData().getTargetingSettings() == null
                    || doc.getDiscountData().getTargetingSettings().getBudget() == null) {
                continue;
            }
            ShopifyDiscountDO.DiscountData.TargetingSettings.Budget budget = doc.getDiscountData().getTargetingSettings().getBudget();
            Boolean currentEnable = doc.getDiscountData().getBasicInformation().getEnable();
            double usedTotal = budget.getUsedTotalBudget() != null ? budget.getUsedTotalBudget() : 0d;
            Double totalBudget = budget.getTotalBudget();

            // 恢复条件：当前 enable == false 且 usedTotalBudget < totalBudget（昨日日限额熔断可恢复）
            boolean shouldRecover = Boolean.FALSE.equals(currentEnable)
                    && totalBudget != null
                    && usedTotal < totalBudget;
            Boolean newEnable = shouldRecover ? true : currentEnable;

            shopifyDiscountCosmos.patchDailyBudgetResetAndEnable(discountId, shopName, newEnable);
        }
    }

    /**
     * 更新折扣数据：从阿里云 SLS 查询前一天的 exposure_pv、checkout_started_pv、gmv、add_to_cart_pv，累积写入数据库。
     */
    public void updateDiscountData() {
        List<String> shopNames = bundleUsersRepo.getAllShopNames();
        if (shopNames == null || shopNames.isEmpty()) {
            return;
        }

        int from = getYesterdayStartTimestamp();
        int to = getYesterdayEndTimestamp();

        for (String shopName : shopNames) {
            List<BundleUsersDiscountDO> discounts = bundleUsersDiscountRepo.getAllByShopName(shopName);
            if (discounts == null || discounts.isEmpty()) {
                continue;
            }

            for (BundleUsersDiscountDO discount : discounts) {
                String discountName = discount.getDiscountName();
                if (discountName == null || discountName.isEmpty()) {
                    continue;
                }

                int exposurePv = queryExposurePv(shopName, discountName, from, to);
                int checkoutStartedPv = queryCheckoutStartedPv(shopName, discountName, from, to);
                int addToCartPv = queryAddToCartPv(shopName, discountName, from, to);
                double gmv = queryGmv(shopName, discountName, from, to);

                bundleUsersDiscountRepo.updateAccumulateDiscountData(
                        shopName,
                        discount.getDiscountId(),
                        exposurePv,
                        addToCartPv,
                        checkoutStartedPv,
                        gmv
                );
            }
        }
    }

    /** 前一天 0 点的时间戳（秒） */
    private int getYesterdayStartTimestamp() {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate yesterday = ZonedDateTime.now(zone).toLocalDate().minusDays(1);
        return (int) yesterday.atStartOfDay(zone).toEpochSecond();
    }

    /** 前一天 24 点（即当天 0 点前一秒）的时间戳（秒） */
    private int getYesterdayEndTimestamp() {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate yesterday = ZonedDateTime.now(zone).toLocalDate().minusDays(1);
        return (int) yesterday.plusDays(1).atStartOfDay(zone).toEpochSecond() - 1;
    }

    private int queryExposurePv(String shopName, String discountName, int from, int to) {
        String query = AliyunLogSqlUtils.getPvByShopNameAndBundleTitleIsNotNull(shopName, BundleExposureService.PRODUCT_VIEWED, discountName);
        List<Map<String, String>> maps = aliyunSlsIntegration.readLogs(from, to, query);
        if (maps != null && !maps.isEmpty()) {
            String pv = maps.get(0).getOrDefault("pv", "0");
            try {
                return Integer.parseInt(pv);
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private int queryCheckoutStartedPv(String shopName, String discountName, int from, int to) {
        String query = AliyunLogSqlUtils.getPvByShopNameAndBundleTitleIsNotNull(shopName, BundleExposureService.CHECKOUT_STARTED, discountName);
        List<Map<String, String>> maps = aliyunSlsIntegration.readLogs(from, to, query);
        if (maps != null && !maps.isEmpty()) {
            String pv = maps.get(0).getOrDefault("pv", "0");
            try {
                return Integer.parseInt(pv);
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private int queryAddToCartPv(String shopName, String discountName, int from, int to) {
        String query = AliyunLogSqlUtils.getProductAddedToCartPvByShopNameAndBundleTitle(shopName, discountName, BundleExposureService.PRODUCT_ADDED_TO_CART);
        List<Map<String, String>> maps = aliyunSlsIntegration.readLogs(from, to, query);
        if (maps != null && !maps.isEmpty()) {
            String pv = maps.get(0).getOrDefault("pv", maps.get(0).getOrDefault("PV", "0"));
            try {
                return Integer.parseInt(pv);
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private double queryGmv(String shopName, String discountName, int from, int to) {
        String query = AliyunLogSqlUtils.getTotalPriceByShopNameAndBundleTitle(shopName, discountName, BundleExposureService.CHECKOUT_COMPLETED);
        List<Map<String, String>> maps = aliyunSlsIntegration.readLogs(from, to, query);
        if (maps == null || maps.isEmpty()) {
            return 0D;
        }
        double totalUsd = 0D;
        for (Map<String, String> row : maps) {
            String currency = row.get("currency");
            String amountStr = row.get("total_bundle_amount");
            if (currency == null || amountStr == null || amountStr.isEmpty()) {
                continue;
            }
            try {
                double amount = Double.parseDouble(amountStr.trim());
                double rate = rateDataService.getRateByRateMap(currency, "USD");
                totalUsd += amount * rate;
            } catch (NumberFormatException ignored) {
            }
        }
        return totalUsd;
    }
}
