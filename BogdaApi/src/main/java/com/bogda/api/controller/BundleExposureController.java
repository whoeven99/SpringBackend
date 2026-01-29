package com.bogda.api.controller;

import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.entity.VO.BundleExposureVO;
import com.bogda.service.logic.bundle.BundleExposureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bundle/exposure")
public class BundleExposureController {
    @Autowired
    private BundleExposureService bundleExposureService;

    // 产品指定天数内的uv数据 visitor
    @PostMapping("/productUvByTimeAndShopName")
    public BaseResponse<Object> productUvByTimeAndShopName(@RequestParam String shopName, @RequestParam Integer day) {
        return bundleExposureService.productUvByTimeAndShopName(shopName, day);
    }

    // 查询产品曝光的pv数据
    @PostMapping("/productExposurePvByShopName")
    public BaseResponse<Object> getProductExposurePvByShopName(@RequestParam String shopName) {
        return bundleExposureService.getProductExposurePvByShopName(shopName);
    }

    // 产品指定天数内的加购pv数据
    @PostMapping("/productPvByTimeAndShopName")
    public BaseResponse<Object> productPvByTimeAndShopName(@RequestParam String shopName, @RequestParam Integer day) {
        return bundleExposureService.productPvByTimeAndShopName(shopName, day);
    }

    // bundle orders = 享受优惠的订单数据
    @PostMapping("/bundleOrdersByTimeAndShopName")
    public BaseResponse<Object> bundleOrdersByTimeAndShopName(@RequestParam String shopName, @RequestParam Integer day) {
        return bundleExposureService.bundleOrdersByTimeAndShopName(shopName, day);
    }

    // 获取用户conversion to bundle
    @PostMapping("/getConversionToBundle")
    public BaseResponse<Object> getConversionToBundle(@RequestParam String shopName, @RequestParam Integer day) {
        return bundleExposureService.conversionToBundleByTimeAndShopName(shopName, day);
    }

    // 获取用户金额数据
    @PostMapping("/getConversionToBundleAmount")
    public BaseResponse<Object> getConversionToBundleAmount(@RequestParam String shopName, @RequestParam Integer day) {
        return bundleExposureService.getAmountByTimeAndUserId(shopName, day);
    }
}
