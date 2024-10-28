package com.bogdatech.controller;

import com.bogdatech.logic.CurrencyService;
import com.bogdatech.model.controller.request.CurrencyRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CurrencyController {

    @Autowired
    private CurrencyService currencyService;

    @PostMapping("/currency/addCurrency")
    public BaseResponse addCurrency(@RequestBody CurrencyRequest request) {
        return currencyService.addCurrency(request);
    }

    @PostMapping("/currency/updateCurrency")
    public BaseResponse updateCurrency(@RequestBody CurrencyRequest request) {
        return currencyService.updateCurrency(request);
    }

    @PostMapping("/currency/deleteCurrency")
    public BaseResponse deleteCurrency(@RequestBody CurrencyRequest request) {
        return currencyService.deleteCurrency(request);
    }

    @PostMapping("/currency/getCurrencyByShopName")
    public BaseResponse getCurrencyByShopName(@RequestBody CurrencyRequest request) {
        return currencyService.getCurrencyByShopName(request);
    }
}
