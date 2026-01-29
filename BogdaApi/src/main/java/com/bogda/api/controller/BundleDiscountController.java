package com.bogda.api.controller;

import com.bogda.common.controller.response.BaseResponse;
import com.bogda.service.logic.BundleApp.BundleDiscountService;
import com.bogda.repository.container.ShopifyDiscountDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bundle/discount")
public class BundleDiscountController {
    @Autowired
    private BundleDiscountService bundleDiscountService;

    // 保存前端传的折扣信息
    @PostMapping("/saveUserDiscount")
    public BaseResponse<Object> saveUserDiscount(@RequestParam String shopName, @RequestBody ShopifyDiscountDO shopifyDiscountDO) {
        return bundleDiscountService.saveUserDiscount(shopName, shopifyDiscountDO);
    }

    // 获取cosmos存的用户折扣信息
    @PostMapping("/getUserDiscount")
    public BaseResponse<Object> getUserDiscount(@RequestParam String shopName, @RequestParam String discountGid) {
        return bundleDiscountService.getUserDiscount(shopName, discountGid);
    }

    // 删除cosmos存的用户折扣信息
    @PostMapping("/deleteUserDiscount")
    public BaseResponse<Object> deleteUserDiscount(@RequestParam String shopName, @RequestParam String discountGid) {
        return bundleDiscountService.deleteUserDiscount(shopName, discountGid);
    }

    // 批量查询用户基本折扣信息
    @PostMapping("/batchQueryUserDiscount")
    public BaseResponse<Object> batchQueryUserDiscount(@RequestParam String shopName) {
        return bundleDiscountService.batchQueryUserDiscount(shopName);
    }

    // 修改用户折扣信息
    @PostMapping("/updateUserDiscount")
    public BaseResponse<Object> updateUserDiscount(@RequestParam String shopName, @RequestBody ShopifyDiscountDO shopifyDiscountDO) {
        return bundleDiscountService.updateUserDiscount(shopName, shopifyDiscountDO);
    }

    // 单独修改用户状态接口
    @PostMapping("/updateUserDiscountStatus")
    public BaseResponse<Object> updateUserDiscountStatus(@RequestParam String shopName, @RequestParam String discountGid, @RequestParam String status) {
        return bundleDiscountService.updateUserDiscountStatus(shopName, discountGid, status);
    }

    // 获取Active Offers，status为true的数量
    @PostMapping("/getActiveOffersByUser")
    public BaseResponse<Object> getActiveOffersByUser(@RequestParam String shopName) {
        return bundleDiscountService.getActiveOffersByUser(shopName);
    }

    // 获取用户所有数据
    @PostMapping("/getAllUserDiscount")
    public BaseResponse<Object> getAllUserDiscount(@RequestParam String shopName) {
        return bundleDiscountService.getAllUserDiscount(shopName);
    }

    // 获取用户折扣为ture 的Total GMV（所有的）
    @PostMapping("/getTotalGMV")
    public BaseResponse<Object> getTotalGMV(@RequestParam String shopName) {
        return bundleDiscountService.getTotalGMV(shopName);
    }

    // 获取用户折扣为true 的 Avg. Conversion
    @PostMapping("/getAvgConversion")
    public BaseResponse<Object> getAvgConversion(@RequestParam String shopName) {
        return bundleDiscountService.getAvgConversion(shopName);
    }
}
