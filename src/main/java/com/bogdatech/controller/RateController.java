package com.bogdatech.controller;

import com.bogdatech.integration.RateHttpIntegration;
import com.bogdatech.logic.RateDataService;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rate")
public class RateController {

    private final RateHttpIntegration rateHttpIntegration;
    private final RateDataService rateDataService;
    @Autowired
    public RateController(RateHttpIntegration rateHttpIntegration, RateDataService rateDataService) {
        this.rateHttpIntegration = rateHttpIntegration;
        this.rateDataService = rateDataService;
    }
    //重新实现的获取135条货币的汇率信息，存入RateMap中
    @GetMapping("/getRate")
    public BaseResponse<Object> getRate() {
        rateHttpIntegration.getFixerRate();
        return new BaseResponse<>().CreateSuccessResponse(200);
    }

    //输入两条货币信息，通过计算获取之间的汇率
    @GetMapping("/getRateByCurrency")
    public BaseResponse<Object> getRateByCurrency(@RequestParam("from") String from,
                                                  @RequestParam("to") String to) {
        return new BaseResponse<>().CreateSuccessResponse(
                rateDataService.getRateByRateMap(from, to));
    }

    @GetMapping("/getRateValue")
    public BaseResponse<Object> getRateValue() {
        return new BaseResponse<>().CreateSuccessResponse(rateDataService.getData());
    }

    @GetMapping("/getRateRule")
    public BaseResponse<Object> getRateRule() {
        return new BaseResponse<>().CreateSuccessResponse(rateDataService.getRateRule());
    }
}
