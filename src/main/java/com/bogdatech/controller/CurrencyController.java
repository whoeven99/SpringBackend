package com.bogdatech.controller;

import com.bogdatech.Service.ICurrenciesService;
import com.bogdatech.entity.CurrenciesDO;
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
    public BaseResponse<Object> addCurrency(@RequestBody CurrenciesDO currenciesDO) {
        return currencyService.insertCurrency(currenciesDO);
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
    public BaseResponse<Object> getCurrencyByShopId(@RequestBody CurrenciesDO request) {
        return new BaseResponse<>().CreateSuccessResponse(purchaseService.getCacheData(request));
    }

   //对currency的初始化方法，添加默认代码
    @GetMapping("/initCurrency")
    public BaseResponse<Object> initCurrency(String shopName) {
        return currencyService.initCurrency(shopName);
    }
}
