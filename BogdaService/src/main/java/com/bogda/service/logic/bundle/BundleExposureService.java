package com.bogda.service.logic.bundle;

import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.entity.VO.BundleExposureVO;
import com.bogda.common.utils.AliyunLogSqlUtils;
import com.bogda.common.utils.JsonUtils;
import com.bogda.integration.aimodel.AliyunSlsIntegration;
import com.fasterxml.jackson.core.type.TypeReference;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class BundleExposureService {
    @Autowired
    private AliyunSlsIntegration aliyunSlsIntegration;

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
        if (flag){
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
        if (maps != null && !maps.isEmpty()){
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
        if (maps != null && !maps.isEmpty()){
            String map = maps.get(0).getOrDefault("valid_bundle_order_count", "0");
            return new BaseResponse<>().CreateSuccessResponse(map);
        }

        return new BaseResponse<>().CreateSuccessResponse(maps);
    }

    // conversion to bundle = 每一个bundle的转化率（下单pv/曝光pv）
    public BaseResponse<Object> conversionToBundleByTimeAndShopName(String shopName, Integer days) {
        Pair<Integer, Integer> timestamps = getTimestamps(days);
        int productExposurePVData = 0;
        int checkoutCompletedPVData = 0;
        String exposurePv = AliyunLogSqlUtils.getPvByShopNameAndEventName(shopName, PRODUCT_VIEWED);
        List<Map<String, String>> maps = aliyunSlsIntegration.readLogs(timestamps.getFirst(), timestamps.getSecond(), exposurePv);
        if (maps != null && !maps.isEmpty()){
            productExposurePVData = Integer.parseInt(maps.get(0).getOrDefault("pv", "0"));
        }

        String checkoutCompletedPV = AliyunLogSqlUtils.getPvByShopNameAndEventName(shopName, CHECKOUT_COMPLETED);
        List<Map<String, String>> maps1 = aliyunSlsIntegration.readLogs(timestamps.getFirst(), timestamps.getSecond(), checkoutCompletedPV);
        if (maps1 != null && !maps1.isEmpty()){
            checkoutCompletedPVData = Integer.parseInt(maps1.get(0).getOrDefault("pv", "0"));
        }

        if (productExposurePVData == 0 || checkoutCompletedPVData == 0){
            return new BaseResponse<>().CreateSuccessResponse(0);
        }

        return new BaseResponse<>().CreateSuccessResponse(0);
    }



    // TODO: 计算total GMV(累积，存db查询)
//    public BaseResponse<Object> totalGMVByTimeAndShopName(String shopName, Integer days) {
//
//    }
    // TODO: avg conversion = 所有优惠的 conversion 的平均数(累积，存db查询)

    // 查询加购（product_added_to_cart）的PV数据
    public BaseResponse<Object> productPvByTimeAndShopName(String shopName, Integer days) {
        // 计算时间范围
        long currentTime = System.currentTimeMillis() / 1000;
        int from = (int)(currentTime - days * 24 * 60 * 60);
        int to = (int)currentTime;
        String productAddedToCartPvByShopName = AliyunLogSqlUtils.getPvByShopNameAndEventName(shopName, PRODUCT_ADDED_TO_CART);
        List<Map<String, String>> maps = aliyunSlsIntegration.readLogs(from, to, productAddedToCartPvByShopName);
        if (maps != null && !maps.isEmpty()){
            String map = maps.get(0).getOrDefault("pv", "0");
            return new BaseResponse<>().CreateSuccessResponse(map);
        }

        return new BaseResponse<>().CreateSuccessResponse(maps);
    }

    // 查询产品曝光的PV数据（根据shopName）
    public BaseResponse<Object> getProductExposurePvByShopName(String shopName) {
        String productExposurePvByShopName = AliyunLogSqlUtils.getPvByShopNameAndEventName(shopName, PRODUCT_VIEWED);
        List<Map<String, String>> maps = aliyunSlsIntegration.readLogs(0, 0, productExposurePvByShopName);
        if (maps != null && !maps.isEmpty()){
            String map = maps.get(0).getOrDefault("pv", "0");
            return new BaseResponse<>().CreateSuccessResponse(map);
        }

        return new BaseResponse<>().CreateSuccessResponse(maps);
    }

    // 计算从今天到指定时间的时间戳
    private Pair<Integer, Integer> getTimestamps(int days) {
        long currentTime = System.currentTimeMillis() / 1000;
        int from = (int)(currentTime - days * 24 * 60 * 60);
        int to = (int)currentTime;
        return new Pair<>(from, to);
    }
}
