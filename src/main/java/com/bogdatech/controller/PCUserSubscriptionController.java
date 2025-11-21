package com.bogdatech.controller;

import com.bogdatech.logic.PCApp.PCUserSubscriptionService;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pc/userSubscription")
public class PCUserSubscriptionController {
    @Autowired
    private PCUserSubscriptionService pcUserSubscriptionService;

    // 切换用户计划
    @PostMapping("/checkUserPlan")
    public BaseResponse<Object> checkUserPlan(@RequestParam String shopName, @RequestParam Integer planId) {
        return pcUserSubscriptionService.checkUserPlan(shopName, planId);
    }
}
