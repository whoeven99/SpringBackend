package com.bogda.service.logic.bundle;

import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.entity.VO.BundleExposureVO;
import com.bogda.common.utils.AliyunLogSqlUtils;
import com.bogda.common.utils.JsonUtils;
import com.bogda.integration.aimodel.AliyunSlsIntegration;
import com.bogda.service.logic.RateDataService;
import com.fasterxml.jackson.core.type.TypeReference;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Component
public class BundleExposureService {
    @Autowired
    private AliyunSlsIntegration aliyunSlsIntegration;
    @Autowired
    private RateDataService rateDataService;

    // 曝光
    public final String PRODUCT_VIEWED = "product_viewed";

    // 加购
    public final String PRODUCT_ADDED_TO_CART = "product_added_to_cart";

    // 创建订单
    public final String CHECKOUT_STARTED = "checkout_started";

    // 下单
    public final String CHECKOUT_COMPLETED = "checkout_completed";

    public BaseResponse<Object> productExposure(BundleExposureVO bundleExposureVO) {
        Map<String, String> logMap = JsonUtils.OBJECT_MAPPER.convertValue(bundleExposureVO, new TypeReference<Map<String, String>>() {
        });

        boolean flag = aliyunSlsIntegration.writeLogs(bundleExposureVO.getEvent(), bundleExposureVO.getShopName(), logMap);
        if (flag) {
            return new BaseResponse<>().CreateSuccessResponse(true);
        }
        return new BaseResponse<>().CreateErrorResponse("写入日志失败");
    }

    // 查询visitor 数据
    public BaseResponse<Object> productUvByTimeAndShopName(String shopName, Integer days) {
        // 计算时间范围
        Pair<Integer, Integer> timestamps = getTimestamps(days);
        String productExposureUvByShopName = AliyunLogSqlUtils.getUvByShopNameAndEventName(shopName, PRODUCT_VIEWED);
        List<Map<String, String>> maps = aliyunSlsIntegration.readLogs(timestamps.getFirst(), timestamps.getSecond(), productExposureUvByShopName);
        if (maps != null && !maps.isEmpty()) {
            String map = maps.get(0).getOrDefault("uv", "0");
            return new BaseResponse<>().CreateSuccessResponse(map);
        }

        return new BaseResponse<>().CreateSuccessResponse(maps);
    }

    // bundle orders 有一个订单id，并且订单id上报的数据中有我们折扣的数据就+1
    public BaseResponse<Object> bundleOrdersByTimeAndShopName(String shopName, Integer days) {
        Pair<Integer, Integer> timestamps = getTimestamps(days);
        String productExposureUvByShopName = AliyunLogSqlUtils.getOrderCountByShopName(shopName, CHECKOUT_COMPLETED);
        List<Map<String, String>> maps = aliyunSlsIntegration.readLogs(timestamps.getFirst(), timestamps.getSecond(), productExposureUvByShopName);
        if (maps != null && !maps.isEmpty()) {
            String map = maps.get(0).getOrDefault("valid_bundle_order_count", "0");
            return new BaseResponse<>().CreateSuccessResponse(map);
        }

        return new BaseResponse<>().CreateSuccessResponse(maps);
    }

    // conversion to bundle = 每一个bundle的转化率（下单pv/曝光pv）
    public BaseResponse<Object> conversionToBundleByTimeAndShopName(String shopName, Integer days) {
        Pair<Integer, Integer> timestamps = getTimestamps(days);
        double productExposurePVData = 0D;
        double checkoutCompletedPVData = 0D;
        String exposurePv = AliyunLogSqlUtils.getPvByShopNameAndBundleTitleIsNotNull(shopName, PRODUCT_VIEWED);
        List<Map<String, String>> maps = aliyunSlsIntegration.readLogs(timestamps.getFirst(), timestamps.getSecond(), exposurePv);
        if (maps != null && !maps.isEmpty()) {
            productExposurePVData = Double.parseDouble(maps.get(0).getOrDefault("pv", "0"));
        }

        String checkoutCompletedPV = AliyunLogSqlUtils.getPvByShopNameAndBundleTitleIsNotNull(shopName, CHECKOUT_COMPLETED);
        List<Map<String, String>> maps1 = aliyunSlsIntegration.readLogs(timestamps.getFirst(), timestamps.getSecond(), checkoutCompletedPV);
        if (maps1 != null && !maps1.isEmpty()) {
            checkoutCompletedPVData = Double.parseDouble(maps1.get(0).getOrDefault("pv", "0"));
        }

        if (productExposurePVData == 0 || checkoutCompletedPVData == 0) {
            return new BaseResponse<>().CreateSuccessResponse(0);
        }

        return new BaseResponse<>().CreateSuccessResponse(checkoutCompletedPVData / productExposurePVData);
    }

    // 查询加购（product_added_to_cart）的PV数据
    public BaseResponse<Object> productPvByTimeAndShopName(String shopName, Integer days) {
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
    public BaseResponse<Object> getAmountByTimeAndUserId(String shopName, Integer days) {
        Pair<Integer, Integer> timestamps = getTimestamps(days);
        String amountByUserId = AliyunLogSqlUtils.getTotalPriceByShopName(shopName, CHECKOUT_STARTED, days);
        List<Map<String, String>> maps = aliyunSlsIntegration.readLogs(timestamps.getFirst(), timestamps.getSecond(), amountByUserId);
        if (maps != null && !maps.isEmpty()) {
            List<Map<String, String>> result = processMapsToUsdByDate(maps);
            return new BaseResponse<>().CreateSuccessResponse(result);
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
            return new BaseResponse<>().CreateSuccessResponse(finalAmount.get());
        }
        return new BaseResponse<>().CreateErrorResponse(0);
    }
}
