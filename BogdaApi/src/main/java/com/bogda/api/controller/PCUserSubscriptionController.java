package com.bogda.api.controller;

import com.bogda.common.controller.response.BaseResponse;
import org.springframework.web.bind.annotation.*;

import static com.bogda.api.support.DisabledProductEndpoints.error;

@RestController
@RequestMapping("/pc/userSubscription")
public class PCUserSubscriptionController {

    @PostMapping("/checkUserPlan")
    public BaseResponse<Object> checkUserPlan(
            @RequestParam String shopName,
            @RequestParam Integer planId,
            @RequestParam Integer feeType) {
        return error();
    }

    @GetMapping("/getUserSubscriptionPlan")
    public BaseResponse<Object> getUserSubscriptionPlan(@RequestParam String shopName) {
        return error();
    }
}
