package com.bogdatech.controller;

import com.bogdatech.Service.ICurrenciesService;
import com.bogdatech.logic.PurchaseService;
import com.bogdatech.model.controller.request.CurrencyRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/currency")
public class CurrencyController {

    @Autowired
    private ICurrenciesService currencyService;

    @Autowired
    private PurchaseService purchaseService;

    @PostMapping("/insertCurrency")
    public BaseResponse<Object> addCurrency(@RequestBody CurrencyRequest request) {
        return currencyService.insertCurrency(request);
    }

    @PutMapping("/updateCurrency")
    public BaseResponse<Object> updateCurrency(@RequestBody CurrencyRequest request) {
        return currencyService.updateCurrency(request);
    }

    @DeleteMapping("/deleteCurrency")
    public BaseResponse<Object> deleteCurrency(@RequestBody CurrencyRequest request) {
        return currencyService.deleteCurrency(request);
    }

    @GetMapping("/getCurrencyByShopName")
    public BaseResponse<Object> getCurrencyByShopName(String shopName) {
        return currencyService.getCurrencyByShopName(shopName);
    }

    @PostMapping("/getCacheData")
    public BaseResponse<Object> getCurrencyByShopId(@RequestBody CurrencyRequest request) {
        return new BaseResponse<>().CreateSuccessResponse(purchaseService.getCacheData(request));
    }


}
