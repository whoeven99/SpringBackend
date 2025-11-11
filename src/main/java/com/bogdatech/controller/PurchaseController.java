package com.bogdatech.controller;

import com.bogdatech.logic.PurchaseService;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/purchase")
public class PurchaseController {
    @Autowired
    private PurchaseService purchaseService;

    //付费表单推荐购买字符数（根据商店总字符数推荐）
    @GetMapping("/getRecommendation")
    public BaseResponse<Object> getRecommendation() {
        return new BaseResponse<>().CreateSuccessResponse(purchaseService.recommendPurchaseAmount());
    }
}
