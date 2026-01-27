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

    // 产品曝光
    @PostMapping("/productView")
    public BaseResponse<Object> productView(@RequestParam String shopName, @RequestBody BundleExposureVO bundleExposureVO) {
        bundleExposureVO.setShopName(shopName);
        return bundleExposureService.productView(bundleExposureVO);
    }

    // 产品指定天数内的uv数据
    @PostMapping("/productUvByTimeAndShopName")
    public BaseResponse<Object> productUvByTimeAndShopName(@RequestParam String shopName, @RequestParam Integer day) {
        return bundleExposureService.productUvByTimeAndShopName(shopName, day);
    }

    // 产品指定天数内的pv数据
}
