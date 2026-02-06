package com.bogda.api.controller;

import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.entity.DTO.BundleAvgConversionIndicatorDTO;
import com.bogda.common.entity.VO.BundleQueryVO;
import com.bogda.service.logic.BundleApp.BundleExposureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bundle/exposure")
public class BundleExposureController {
    @Autowired
    private BundleExposureService bundleExposureService;

    // 产品指定天数内的uv数据 visitor
    @PostMapping("/productUvByTimeAndShopName")
    public BaseResponse<Object> productUvByTimeAndShopName(@RequestParam String shopName, @RequestBody BundleQueryVO bundleQueryVO) {
        bundleQueryVO.setShopName(shopName);
        return bundleExposureService.productUvByTimeAndShopName(shopName, bundleQueryVO.getDay(), bundleQueryVO.getBundleId());
    }

    // 查询产品曝光的pv数据
    @PostMapping("/productExposurePvByShopName")
    public BaseResponse<Object> getProductExposurePvByShopName(@RequestParam String shopName) {
        return bundleExposureService.getProductExposurePvByShopName(shopName);
    }

    // 产品指定天数内的加购pv数据
    @PostMapping("/productPvByTimeAndShopName")
    public BaseResponse<Object> productPvByTimeAndShopName(@RequestParam String shopName, @RequestBody BundleQueryVO bundleQueryVO) {
        bundleQueryVO.setShopName(shopName);
        return bundleExposureService.productPvByTimeAndShopName(shopName, bundleQueryVO.getDay(), bundleQueryVO.getBundleId());
    }

    // bundle orders = 享受优惠的订单数据
    @PostMapping("/bundleOrdersByTimeAndShopName")
    public BaseResponse<Object> bundleOrdersByTimeAndShopName(@RequestParam String shopName, @RequestBody BundleQueryVO bundleQueryVO) {
        bundleQueryVO.setShopName(shopName);
        return bundleExposureService.bundleOrdersByTimeAndShopName(shopName, bundleQueryVO.getDay(), bundleQueryVO.getBundleId());
    }

    // 获取用户conversion to bundle
    @PostMapping("/getConversionToBundle")
    public BaseResponse<Object> getConversionToBundle(@RequestParam String shopName, @RequestBody BundleQueryVO bundleQueryVO) {
        bundleQueryVO.setShopName(shopName);
        return bundleExposureService.conversionToBundleByTimeAndShopName(shopName, bundleQueryVO.getDay(), bundleQueryVO.getBundleId());
    }

    // 获取用户60天 bundle orders 和 对应指标
    @PostMapping("/getBundleOrdersIndicator")
    public BaseResponse<Object> getBundleOrdersIndicator(@RequestParam String shopName) {
        return bundleExposureService.getBundleOrdersIndicator(shopName);
    }

    // 获取用户金额数据 daliy add revenue
    @PostMapping("/getConversionToBundleAmount")
    public BaseResponse<Object> getConversionToBundleAmount(@RequestParam String shopName, @RequestBody BundleQueryVO bundleQueryVO) {
        bundleQueryVO.setShopName(shopName);
        return bundleExposureService.getAmountByTimeAndUserId(shopName, bundleQueryVO.getDay(), bundleQueryVO.getBundleId());
    }

    // 获取用户指定时间totalGMV
    @PostMapping("/getTotalGMV")
    public BaseResponse<Object> getTotalGMV(@RequestParam String shopName) {
        return bundleExposureService.getTotalGMV(shopName, 60);
    }

    // 获取用户指定时间totalGMV下的指标  上个月和这个月的对比
    @PostMapping("/getTotalGMVIndicator")
    public BaseResponse<Object> getTotalGMVIndicator(@RequestParam String shopName) {
        return bundleExposureService.getTotalGMVIndicator(shopName, 60);
    }

    // 获取用户折扣为true 的 Avg. Conversion
    @PostMapping("/getAvgConversion")
    public BaseResponse<Object> getAvgConversion(@RequestParam String shopName) {
        BaseResponse<Object> response = bundleExposureService.getAvgConversion(shopName);
        Object data = response.getResponse();
        if (data instanceof BundleAvgConversionIndicatorDTO) {
            BundleAvgConversionIndicatorDTO dto = (BundleAvgConversionIndicatorDTO) data;
            if (dto.getAvgConversionIndicator() == null || Double.isNaN(dto.getAvgConversionIndicator()) || Double.isInfinite(dto.getAvgConversionIndicator())) {
                dto.setAvgConversionIndicator(0D);
            }
            if (dto.getAvgConversion() == null || Double.isNaN(dto.getAvgConversion()) || Double.isInfinite(dto.getAvgConversion())) {
                dto.setAvgConversion(0D);
            }
        }
        return response;
    }
}
