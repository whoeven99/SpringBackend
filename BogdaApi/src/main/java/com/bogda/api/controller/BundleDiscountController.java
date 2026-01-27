package com.bogda.web.controller;

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
}
