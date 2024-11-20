package com.bogdatech.controller;

import com.bogdatech.integration.RateHttpIntegration;
import com.bogdatech.logic.RateDataService;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RateController {
    @Autowired
    private RateHttpIntegration rateHttpIntegration;

    @Autowired
    private RateDataService rateDataService;

    @GetMapping("/getRate")
    public BaseResponse getRate() {
        return new BaseResponse().CreateSuccessResponse(
                rateHttpIntegration.getFixerRate());
    }

    @PostMapping("/getRateValue")
    public BaseResponse getRateValue(){
        return new BaseResponse().CreateSuccessResponse(rateDataService.getData());
    }
}
