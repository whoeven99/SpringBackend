package com.bogdatech.logic.PCApp;

import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.repository.repo.PCUserSubscriptionsRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PCUserSubscriptionService {
    @Autowired
    private PCUserSubscriptionsRepo pcUserSubscriptionsRepo;
    public BaseResponse<Object> checkUserPlan(String shopName, Integer planId) {
        boolean update = pcUserSubscriptionsRepo.checkUserPlan(shopName, planId);
        if (update){
            return new BaseResponse<>().CreateSuccessResponse(true);
        }
        return new BaseResponse<>().CreateErrorResponse("check error");
    }
}
