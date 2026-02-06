package com.bogda.service.logic.BundleApp;

import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.entity.DTO.*;
import com.bogda.common.utils.AliyunLogSqlUtils;
import com.bogda.integration.aimodel.AliyunSlsIntegration;
import com.bogda.repository.repo.bundle.BundleUsersDiscountRepo;
import com.bogda.service.logic.RateDataService;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Component
public class BundleExposureService {
    @Autowired
    private AliyunSlsIntegration aliyunSlsIntegration;
    @Autowired
    private RateDataService rateDataService;
    @Autowired
    private BundleUsersDiscountRepo bundleUsersDiscountRepo;

    // 曝光
    public static final String PRODUCT_VIEWED = "product_viewed";

    // 加购
    public static final String PRODUCT_ADDED_TO_CART = "product_added_to_cart";

    // 创建订单
    public static final String CHECKOUT_STARTED = "checkout_started";

    // 下单
    public static final String CHECKOUT_COMPLETED = "checkout_completed";

    // 查询visitor 数据
    public BaseResponse<Object> productUvByTimeAndShopName(String shopName, Integer days, String discountId) {
        String productExposureUvByShopName = null;

        if (discountId == null) {
            productExposureUvByShopName = AliyunLogSqlUtils.getUvByShopNameAndEventName(shopName, PRODUCT_VIEWED);
        } else {
            // 获取discount name
            String discountName = bundleUsersDiscountRepo.getDiscountNameByShopNameAndDiscountId(shopName, discountId);
            productExposureUvByShopName = AliyunLogSqlUtils.getProductAddedToCartUvByShopNameAndBundleTitle(shopName, PRODUCT_VIEWED, discountName);
        }

        // 计算时间范围
        Pair<Integer, Integer> timestamps = getTimestamps(days);
        System.out.println("query: " + productExposureUvByShopName);
        List<Map<String, String>> maps = aliyunSlsIntegration.readLogs(timestamps.getFirst(), timestamps.getSecond(), productExposureUvByShopName);
        if (maps != null && !maps.isEmpty()) {
            System.out.println("maps: " + maps);
            String map = maps.get(0).getOrDefault("uv", "0");
            return new BaseResponse<>().CreateSuccessResponse(new BundleVisitorDTO(Integer.parseInt(map)));
        }

        return new BaseResponse<>().CreateSuccessResponse(maps);
    }

    // bundle orders 有一个订单id，并且订单id上报的数据中有我们折扣的数据就+1
    public BaseResponse<Object> bundleOrdersByTimeAndShopName(String shopName, Integer days, String discountId) {
        String productExposureUvByShopName = null;

        if (discountId == null) {
            productExposureUvByShopName = AliyunLogSqlUtils.getOrderCountByShopName(shopName, CHECKOUT_COMPLETED);
        } else {
            // 获取discount name
            String discountName = bundleUsersDiscountRepo.getDiscountNameByShopNameAndDiscountId(shopName, discountId);
            productExposureUvByShopName = AliyunLogSqlUtils.getOrderCountByShopName(shopName, CHECKOUT_COMPLETED, discountName);
        }
        Pair<Integer, Integer> timestamps = getTimestamps(days);

        List<Map<String, String>> maps = aliyunSlsIntegration.readLogs(timestamps.getFirst(), timestamps.getSecond(), productExposureUvByShopName);
        if (maps != null && !maps.isEmpty()) {
            Integer map = Integer.valueOf(maps.get(0).getOrDefault("valid_bundle_order_count", "0"));
            BundleOrdersDTO bundleOrdersDTO = new BundleOrdersDTO();
            bundleOrdersDTO.setBundleOrders(map);
            return new BaseResponse<>().CreateSuccessResponse(bundleOrdersDTO);
        }

        return new BaseResponse<>().CreateSuccessResponse(maps);
    }

    // conversion to bundle = 每一个bundle的转化率（下单pv/曝光pv）
    public BaseResponse<Object> conversionToBundleByTimeAndShopName(String shopName, Integer days, String discountId) {
        String exposurePv = null;
        String exposureUv = null;

        if (discountId == null) {
            exposurePv = AliyunLogSqlUtils.getOrderCountByShopName(shopName, PRODUCT_VIEWED);
            exposureUv = AliyunLogSqlUtils.getPvByShopNameAndBundleTitleIsNotNull(shopName, CHECKOUT_COMPLETED);
        } else {
            // 获取discount name
            String discountName = bundleUsersDiscountRepo.getDiscountNameByShopNameAndDiscountId(shopName, discountId);
            exposurePv = AliyunLogSqlUtils.getOrderCountByShopName(shopName, PRODUCT_VIEWED, discountName);
            exposureUv = AliyunLogSqlUtils.getPvByShopNameAndBundleTitleIsNotNull(shopName, CHECKOUT_COMPLETED, discountName);
        }

        Pair<Integer, Integer> timestamps = getTimestamps(days);
        double productExposurePVData = 0D;
        double checkoutCompletedPVData = 0D;

        List<Map<String, String>> maps = aliyunSlsIntegration.readLogs(timestamps.getFirst(), timestamps.getSecond(), exposurePv);
        if (maps != null && !maps.isEmpty()) {
            productExposurePVData = Double.parseDouble(maps.get(0).getOrDefault("valid_bundle_order_count", "0"));
        }

        List<Map<String, String>> maps1 = aliyunSlsIntegration.readLogs(timestamps.getFirst(), timestamps.getSecond(), exposureUv);
        if (maps1 != null && !maps1.isEmpty()) {
            checkoutCompletedPVData = Double.parseDouble(maps1.get(0).getOrDefault("pv", "0"));
        }

        if (productExposurePVData == 0 || checkoutCompletedPVData == 0) {
            return new BaseResponse<>().CreateSuccessResponse(0);
        }
        DecimalFormat df = new DecimalFormat("0.00");
        double finalData = Double.parseDouble(
                df.format(checkoutCompletedPVData / productExposurePVData * 100)
        );
        return new BaseResponse<>().CreateSuccessResponse(new BundleConversionDTO(finalData));
    }

    // 查询加购（product_added_to_cart）的PV数据
    public BaseResponse<Object> productPvByTimeAndShopName(String shopName, Integer days, String discountId) {
        // 计算时间范围
        long currentTime = System.currentTimeMillis() / 1000;
        int from = (int) (currentTime - days * 24 * 60 * 60);
        int to = (int) currentTime;
        String productAddedToCartPvByShopName = AliyunLogSqlUtils.getPvByShopNameAndEventName(shopName, PRODUCT_ADDED_TO_CART);
        List<Map<String, String>> maps = aliyunSlsIntegration.readLogs(from, to, productAddedToCartPvByShopName);
        if (maps != null && !maps.isEmpty()) {
            String map = maps.get(0).getOrDefault("pv", "0");
            return new BaseResponse<>().CreateSuccessResponse(map);
        }

        return new BaseResponse<>().CreateSuccessResponse(maps);
    }

    // 查询产品曝光的PV数据（根据shopName）
    public BaseResponse<Object> getProductExposurePvByShopName(String shopName) {
        String productExposurePvByShopName = AliyunLogSqlUtils.getPvByShopNameAndEventName(shopName, PRODUCT_VIEWED);
        List<Map<String, String>> maps = aliyunSlsIntegration.readLogs(0, 0, productExposurePvByShopName);
        if (maps != null && !maps.isEmpty()) {
            String map = maps.get(0).getOrDefault("pv", "0");
            return new BaseResponse<>().CreateSuccessResponse(map);
        }

        return new BaseResponse<>().CreateSuccessResponse(maps);
    }

    // 计算从今天到指定时间的时间戳
    private Pair<Integer, Integer> getTimestamps(int days) {
        long currentTime = System.currentTimeMillis() / 1000;
        int from = (int) (currentTime - days * 24 * 60 * 60);
        int to = (int) currentTime;
        return new Pair<>(from, to);
    }

    // 获取指定时间内，指定用户的金额数据
    public BaseResponse<Object> getAmountByTimeAndUserId(String shopName, Integer days, String discountId) {
        String amountByUserId = null;

        if (discountId == null) {
            amountByUserId = AliyunLogSqlUtils.getTotalPriceByShopName(shopName, CHECKOUT_STARTED, days);
        } else {
            // 获取discount name
            String discountName = bundleUsersDiscountRepo.getDiscountNameByShopNameAndDiscountId(shopName, discountId);
            amountByUserId = AliyunLogSqlUtils.getTotalPriceByShopName(shopName, PRODUCT_VIEWED, days, discountName);
        }

        Pair<Integer, Integer> timestamps = getTimestamps(days);

        List<Map<String, String>> maps = aliyunSlsIntegration.readLogs(timestamps.getFirst(), timestamps.getSecond(), amountByUserId);
        if (maps != null && !maps.isEmpty()) {
            List<Map<String, String>> result = processMapsToUsdByDate(maps);
            return new BaseResponse<>().CreateSuccessResponse(new BundleAmountDTO(result));
        }

        return new BaseResponse<>().CreateErrorResponse("No data found");
    }

    /**
     * 对 maps 按 date 分组，将同一天不同 currency 的 daily_total_amount 转为 USD 后求和，
     * 返回按 date 统计的 List<Map<String, Double>>，每个 map 含 "date"(日期对应数值)、"daily_total_amount"(USD 总和)。
     */
    private List<Map<String, String>> processMapsToUsdByDate(List<Map<String, String>> maps) {
        Map<String, List<Map<String, String>>> byDate = maps.stream()
                .filter(m -> m != null && m.get("date") != null)
                .collect(Collectors.groupingBy(m -> m.get("date"), LinkedHashMap::new, Collectors.toList()));

        List<Map<String, String>> result = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, String>>> e : byDate.entrySet()) {
            String dateStr = e.getKey();
            double totalUsd = 0.0;
            for (Map<String, String> row : e.getValue()) {
                String amtStr = row.get("daily_total_amount");
                String currency = row.get("currency");
                if (amtStr == null || "null".equalsIgnoreCase(amtStr) || amtStr.trim().isEmpty()) {
                    continue;
                }
                double amount;
                try {
                    amount = Double.parseDouble(amtStr.trim());
                } catch (NumberFormatException ex) {
                    continue;
                }
                if (currency == null || "null".equalsIgnoreCase(currency) || currency.trim().isEmpty()) {
                    continue;
                }
                double rate = rateDataService.getRateByRateMap(currency, "USD");
                totalUsd += amount * rate;
            }

            Map<String, String> item = new HashMap<>();
            item.put("date", dateStr);
            item.put("daily_total_amount", String.valueOf(totalUsd));
            result.add(item);
        }
        return result;
    }

    public BaseResponse<Object> getTotalGMV(String shopName, Integer day) {
        Pair<Integer, Integer> timestamps = getTimestamps(day);
        String amountByUserId = AliyunLogSqlUtils.getTotalPriceByShopNameAndBundleTitleIsNotNull(shopName, CHECKOUT_COMPLETED);
        List<Map<String, String>> maps = aliyunSlsIntegration.readLogs(timestamps.getFirst(), timestamps.getSecond(), amountByUserId);
        AtomicReference<Double> finalAmount = new AtomicReference<>(0D);
        if (maps != null && !maps.isEmpty()) {
            maps.forEach(map -> {
                // 将货币转化为USD
                double rate = rateDataService.getRateByRateMap(map.get("currency"), "USD");
                finalAmount.updateAndGet(v -> v + Double.parseDouble(map.get("total_sum")) * rate);

            });

            return new BaseResponse<>().CreateSuccessResponse(new BundleGmvDTO(finalAmount.get()));
        }
        return new BaseResponse<>().CreateErrorResponse(0);
    }

    public BaseResponse<Object> getTotalGMVIndicator(String shopName, int day) {
        Pair<Integer, Integer> timestamps = getTimestamps(day);
        String amountByUserId = AliyunLogSqlUtils.getTotalPriceByShopNameAndBundleTitleIsNotNull(shopName, CHECKOUT_COMPLETED);
        List<Map<String, String>> map60 = aliyunSlsIntegration.readLogs(timestamps.getFirst(), timestamps.getSecond(), amountByUserId);
        AtomicReference<Double> final60Amount = new AtomicReference<>(0D);
        if (map60 != null && !map60.isEmpty()) {
            map60.forEach(map -> {
                // 将货币转化为USD
                double rate = rateDataService.getRateByRateMap(map.get("currency"), "USD");
                final60Amount.updateAndGet(v -> v + Double.parseDouble(map.get("total_sum")) * rate);

            });
        }

        Pair<Integer, Integer> timestamp30 = getTimestamps(30);
        List<Map<String, String>> maps = aliyunSlsIntegration.readLogs(timestamp30.getFirst(), timestamp30.getSecond(), amountByUserId);
        AtomicReference<Double> final30Amount = new AtomicReference<>(0D);
        if (maps != null && !maps.isEmpty()) {
            maps.forEach(map -> {
                // 将货币转化为USD
                double rate = rateDataService.getRateByRateMap(map.get("currency"), "USD");
                final30Amount.updateAndGet(v -> v + Double.parseDouble(map.get("total_sum")) * rate);
            });
        }

        double finalLast30Amount = final60Amount.get() - final30Amount.get();
        if (finalLast30Amount == 0) {
            return new BaseResponse<>().CreateSuccessResponse(new BundleGmvIndicatorDTO(0D));
        }

        double result = finalLast30Amount / final30Amount.get() * 100;
        return new BaseResponse<>().CreateSuccessResponse(new BundleGmvIndicatorDTO(result));
    }

    public BaseResponse<Object> getAvgConversion(String shopName) {
        Pair<Integer, Integer> timestamp60 = getTimestamps(60);
        Pair<Integer, Integer> timestamp30 = getTimestamps(30);

        BundleAvgConversionIndicatorDTO bundleAvgConversion = new BundleAvgConversionIndicatorDTO();
        String pvCheckCompleted = AliyunLogSqlUtils.getPvByShopNameAndBundleTitleIsNotNull(shopName, CHECKOUT_COMPLETED);
        String pvProductViewed = AliyunLogSqlUtils.getPvByShopNameAndBundleTitleIsNotNull(shopName, PRODUCT_VIEWED);

        List<Map<String, String>> pvCheckCompleted60 = aliyunSlsIntegration.readLogs(timestamp60.getFirst(), timestamp60.getSecond(), pvCheckCompleted);
        List<Map<String, String>> pvProductViewed60 = aliyunSlsIntegration.readLogs(timestamp60.getFirst(), timestamp60.getSecond(), pvProductViewed);


        List<Map<String, String>> pvCheckCompleted30 = aliyunSlsIntegration.readLogs(timestamp30.getFirst(), timestamp30.getSecond(), pvCheckCompleted);
        List<Map<String, String>> pvProductViewed30 = aliyunSlsIntegration.readLogs(timestamp30.getFirst(), timestamp30.getSecond(), pvProductViewed);

        double pvCheckCompletedCount = 0D;
        double pvProductViewedCount = 0D;
        double pvCheckCompletedCountIndication = 0D;
        double pvProductViewedCountIndication = 0D;

        if (pvCheckCompleted60 != null && !pvCheckCompleted60.isEmpty()) {
            pvCheckCompletedCount = Double.parseDouble(pvCheckCompleted60.get(0).getOrDefault("pv", "0"));
        }

        if (pvProductViewed60 != null && !pvProductViewed60.isEmpty()) {
            pvProductViewedCount = Double.parseDouble(pvProductViewed60.get(0).getOrDefault("pv", "0"));
        }

        if (pvCheckCompleted30 != null && !pvCheckCompleted30.isEmpty()) {
            pvCheckCompletedCountIndication = Double.parseDouble(pvCheckCompleted30.get(0).getOrDefault("pv", "0"));
        }

        if (pvProductViewed30 != null && !pvProductViewed30.isEmpty()) {
            pvProductViewedCountIndication = Double.parseDouble(pvProductViewed30.get(0).getOrDefault("pv", "0"));
        }

        double avgConversion = 0D;
        if (pvProductViewedCount != 0) {
            avgConversion = pvCheckCompletedCount / pvProductViewedCount * 100;
        }
        bundleAvgConversion.setAvgConversion(sanitizeDouble(avgConversion));

        double avgConversionFirst30 = 0D;
        if (pvProductViewedCountIndication != 0) {
            avgConversionFirst30 = pvCheckCompletedCountIndication / pvProductViewedCountIndication * 100;
        }

        double avgConversionIndicator = 0D;
        double lastMonthAvgConversion = avgConversion - avgConversionFirst30;
        if (lastMonthAvgConversion == 0 || avgConversionFirst30 == 0) {
            bundleAvgConversion.setAvgConversionIndicator(0D);
            return new BaseResponse<>().CreateSuccessResponse(bundleAvgConversion);
        }

        avgConversionIndicator = (avgConversionFirst30 - lastMonthAvgConversion) / lastMonthAvgConversion * 100;
        bundleAvgConversion.setAvgConversionIndicator(sanitizeDouble(avgConversionIndicator));
        return new BaseResponse<>().CreateSuccessResponse(bundleAvgConversion);
    }

    public BaseResponse<Object> getBundleOrdersIndicator(String shopName) {
        Pair<Integer, Integer> timestamp60 = getTimestamps(60);
        Pair<Integer, Integer> timestamp30 = getTimestamps(30);

        BundleOrdersDTO bundleOrdersDTO = new BundleOrdersDTO();
        String pv60BundleOrders = AliyunLogSqlUtils.getOrderCountByShopName(shopName, CHECKOUT_COMPLETED);
        String pv30BundleOrders = AliyunLogSqlUtils.getOrderCountByShopName(shopName, CHECKOUT_COMPLETED);

        List<Map<String, String>> pvBundleOrders60 = aliyunSlsIntegration.readLogs(timestamp60.getFirst(), timestamp60.getSecond(), pv60BundleOrders);

        List<Map<String, String>> pvBundleOrders30 = aliyunSlsIntegration.readLogs(timestamp30.getFirst(), timestamp30.getSecond(), pv30BundleOrders);

        double pvBundleOrders60Count = 0D;
        double pvBundleOrders30Count = 0D;

        if (pvBundleOrders60 != null && !pvBundleOrders60.isEmpty()) {
            pvBundleOrders60Count = Double.parseDouble(pvBundleOrders60.get(0).getOrDefault("valid_bundle_order_count", "0"));
        }

        if (pvBundleOrders30 != null && !pvBundleOrders30.isEmpty()) {
            pvBundleOrders30Count = Double.parseDouble(pvBundleOrders30.get(0).getOrDefault("valid_bundle_order_count", "0"));
        }

        bundleOrdersDTO.setBundleOrders((int) pvBundleOrders60Count);
        double bundleOrdersLast30 = 0D;
        bundleOrdersLast30 = pvBundleOrders60Count - pvBundleOrders30Count;

        double bundleOrdersIndicator = 0;


        if (pvBundleOrders30Count == 0 || bundleOrdersLast30 == 0) {
            bundleOrdersDTO.setBundleIndicator(0D);
            return new BaseResponse<>().CreateSuccessResponse(bundleOrdersDTO);
        }

        bundleOrdersIndicator = (pvBundleOrders30Count - bundleOrdersLast30) / bundleOrdersLast30 * 100;
        bundleOrdersDTO.setBundleIndicator(bundleOrdersIndicator);
        return new BaseResponse<>().CreateSuccessResponse(bundleOrdersDTO);
    }

    /**
     * 将 NaN、Infinity 等异常值转换为 0D
     */
    private static double sanitizeDouble(double value) {
        return (Double.isNaN(value) || Double.isInfinite(value)) ? 0D : value;
    }
}
