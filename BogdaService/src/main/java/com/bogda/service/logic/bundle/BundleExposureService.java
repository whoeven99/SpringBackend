package com.bogda.service.logic.bundle;

import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.entity.VO.BundleExposureVO;
import com.bogda.common.utils.AliyunLogSqlUtils;
import com.bogda.common.utils.JsonUtils;
import com.bogda.integration.aimodel.AliyunSlsIntegration;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class BundleExposureService {
    @Autowired
    private AliyunSlsIntegration aliyunSlsIntegration;

    public BaseResponse<Object> productExposure(BundleExposureVO bundleExposureVO) {
        Map<String, String> logMap = JsonUtils.OBJECT_MAPPER.convertValue(bundleExposureVO, new TypeReference<Map<String, String>>() {
        });

        boolean flag = aliyunSlsIntegration.writeLogs(bundleExposureVO.getEvent(), bundleExposureVO.getShopName(), logMap);
        if (flag){
            return new BaseResponse<>().CreateSuccessResponse(true);
        }
        return new BaseResponse<>().CreateErrorResponse("写入日志失败");
    }

    public BaseResponse<Object> productUvByTimeAndShopName(String shopName, Integer days) {
        // 计算时间范围
        long currentTime = System.currentTimeMillis() / 1000;
        int from = (int)(currentTime - days * 24 * 60 * 60);
        int to = (int)currentTime;
        String productExposureUvByShopName = AliyunLogSqlUtils.getProductExposureUvByShopName(shopName);
        List<Map<String, String>> maps = aliyunSlsIntegration.readLogs(from, to, productExposureUvByShopName);
        if (maps != null && !maps.isEmpty()){
            String map = maps.get(0).getOrDefault("uv", "0");
            return new BaseResponse<>().CreateSuccessResponse(map);
        }

        return new BaseResponse<>().CreateSuccessResponse(maps);
    }

    // 查询加购（product_added_to_cart）的PV数据
    public BaseResponse<Object> productPvByTimeAndShopName(String shopName, Integer days) {
        // 计算时间范围
        long currentTime = System.currentTimeMillis() / 1000;
        int from = (int)(currentTime - days * 24 * 60 * 60);
        int to = (int)currentTime;
        String productAddedToCartPvByShopName = AliyunLogSqlUtils.getProductAddedToCartPvByShopName(shopName);
        List<Map<String, String>> maps = aliyunSlsIntegration.readLogs(from, to, productAddedToCartPvByShopName);
        if (maps != null && !maps.isEmpty()){
            String map = maps.get(0).getOrDefault("pv", "0");
            return new BaseResponse<>().CreateSuccessResponse(map);
        }

        return new BaseResponse<>().CreateSuccessResponse(maps);
    }

    // 查询产品曝光的PV数据（根据shopName）
    public BaseResponse<Object> getProductExposurePvByShopName(String shopName) {
        String productExposurePvByShopName = AliyunLogSqlUtils.getProductExposurePvByShopName(shopName);
        List<Map<String, String>> maps = aliyunSlsIntegration.readLogs(0, 0, productExposurePvByShopName);
        if (maps != null && !maps.isEmpty()){
            String map = maps.get(0).getOrDefault("pv", "0");
            return new BaseResponse<>().CreateSuccessResponse(map);
        }

        return new BaseResponse<>().CreateSuccessResponse(maps);
    }
}
