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

    // 产品曝光 产品加购 通过event区分
    @PostMapping("/productExposure")
    public BaseResponse<Object> productExposure(@RequestParam String shopName, @RequestBody BundleExposureVO bundleExposureVO) {
        bundleExposureVO.setShopName(shopName);
        return bundleExposureService.productExposure(bundleExposureVO);
    }

    // 产品指定天数内的uv数据
    @PostMapping("/productUvByTimeAndShopName")
    public BaseResponse<Object> productUvByTimeAndShopName(@RequestParam String shopName, @RequestParam Integer day) {
        return bundleExposureService.productUvByTimeAndShopName(shopName, day);
    }

    // 产品指定天数内的加购pv数据
    @PostMapping("/productPvByTimeAndShopName")
    public BaseResponse<Object> productPvByTimeAndShopName(@RequestParam String shopName, @RequestParam Integer day) {
        return bundleExposureService.productPvByTimeAndShopName(shopName, day);
    }

    // 查询产品曝光的pv数据
    @PostMapping("/getProductExposurePvByShopName")
    public BaseResponse<Object> getProductExposurePvByShopName(@RequestParam String shopName) {
        return bundleExposureService.getProductExposurePvByShopName(shopName);
    }
}
