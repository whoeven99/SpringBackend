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

    //重新实现的获取135条货币的汇率信息，存入RateMap中
    @GetMapping("/getRate")
    public BaseResponse<Object> getRate() {
        return new BaseResponse<>().CreateSuccessResponse(
                rateHttpIntegration.getFixerRate());
    }

    //输入两条货币信息，通过计算获取之间的汇率
    @PostMapping("/getRateByCurrency")
    public BaseResponse<Object> getRateByCurrency(String from, String to) {
        return new BaseResponse<>().CreateSuccessResponse(
                rateDataService.getRateByRateMap(from, to));
    }

    @PostMapping("/getRateValue")
    public BaseResponse<Object> getRateValue() {
        return new BaseResponse<>().CreateSuccessResponse(rateDataService.getData());
    }

    @GetMapping("/rate/getRateRule")
    public BaseResponse<Object> getRateRule() {
        return new BaseResponse<>().CreateSuccessResponse(rateDataService.getRateRule());
    }
}
