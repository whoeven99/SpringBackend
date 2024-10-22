package com.bogdatech.controller;

import com.bogdatech.integration.RateHttpIntegration;
import com.bogdatech.logic.RateDataService;
import com.bogdatech.model.controller.request.BasicRateRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RateController {
    @Autowired
    private RateHttpIntegration rateHttpIntegration;

    @Autowired
    private RateDataService rateDataService;

    @PostMapping("/getRate")
    public BaseResponse getRate(@RequestBody BasicRateRequest basicRateRequest) throws Exception {
        return new BaseResponse().CreateSuccessResponse(
                rateHttpIntegration.getBasicRate(basicRateRequest.getScur(), basicRateRequest.getTcur()));
    }

    @PostMapping("/getRateValue")
    public BaseResponse getRateValue(){
        return new BaseResponse().CreateSuccessResponse(rateDataService.getData());
    }
}
