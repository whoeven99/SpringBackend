package com.bogda.web.controller;

import com.bogda.service.logic.PCApp.PCUserSubscriptionService;
import com.bogda.common.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pc/userSubscription")
public class PCUserSubscriptionController {
    @Autowired
    private PCUserSubscriptionService pcUserSubscriptionService;

    // 切换用户计划
    @PostMapping("/checkUserPlan")
    public BaseResponse<Object> checkUserPlan(@RequestParam String shopName, @RequestParam Integer planId, @RequestParam Integer feeType) {
        return pcUserSubscriptionService.checkUserPlan(shopName, planId, feeType);
    }

    //获取用户订阅计划
    @GetMapping("/getUserSubscriptionPlan")
    public BaseResponse<Object> getUserSubscriptionPlan(@RequestParam String shopName) {
        return pcUserSubscriptionService.getUserSubscriptionPlan(shopName);

    }
}
