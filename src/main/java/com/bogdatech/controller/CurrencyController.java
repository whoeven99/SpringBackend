package com.bogdatech.controller;

import com.bogdatech.Service.ICurrenciesService;
import com.bogdatech.model.controller.request.CurrencyRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CurrencyController {

    @Autowired
    private ICurrenciesService currencyService;

    @PostMapping("/currency/insertCurrency")
    public BaseResponse<Object> addCurrency(@RequestBody CurrencyRequest request) {
        return currencyService.insertCurrency(request);
    }

    @PostMapping("/currency/updateCurrency")
    public BaseResponse<Object> updateCurrency(@RequestBody CurrencyRequest request) {
        return currencyService.updateCurrency(request);
    }

    @PostMapping("/currency/deleteCurrency")
    public BaseResponse<Object> deleteCurrency(@RequestBody CurrencyRequest request) {
        return currencyService.deleteCurrency(request);
    }

    @PostMapping("/currency/getCurrencyByShopName")
    public BaseResponse<Object> getCurrencyByShopName(@RequestBody CurrencyRequest request) {
        return currencyService.getCurrencyByShopName(request);
    }

}
