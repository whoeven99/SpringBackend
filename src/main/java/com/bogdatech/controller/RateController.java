package com.bogdatech.controller;

import com.bogdatech.logic.RateDataService;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.bogdatech.logic.RateDataService.getRateByRateMap;

@RestController
@RequestMapping("/rate")
public class RateController {
    @Autowired
    private RateDataService rateDataService;

    //输入两条货币信息，通过计算获取之间的汇率
    @GetMapping("/getRateByCurrency")
    public BaseResponse<Object> getRateByCurrency(@RequestParam("from") String from,
                                                  @RequestParam("to") String to) {
        return new BaseResponse<>().CreateSuccessResponse(
                getRateByRateMap(from, to));
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
