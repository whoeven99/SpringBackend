package com.bogdatech.controller;

import com.bogdatech.Service.ICurrenciesService;
import com.bogdatech.entity.DO.CurrenciesDO;
import com.bogdatech.logic.PurchaseService;
import com.bogdatech.model.controller.request.CurrencyRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/currency")
public class CurrencyController {

    private final ICurrenciesService currencyService;
    private final PurchaseService purchaseService;
    @Autowired
    public CurrencyController(ICurrenciesService currencyService, PurchaseService purchaseService) {
        this.currencyService = currencyService;
        this.purchaseService = purchaseService;
    }

    //根据传入的货币代码插入货币信息
    @PostMapping("/insertCurrency")
    public BaseResponse<Object> addCurrency(@RequestBody CurrenciesDO currenciesDO) {
        return currencyService.insertCurrency(currenciesDO);
    }

    //根据传入的货币代码更新对应的货币信息
    @PutMapping("/updateCurrency")
    public BaseResponse<Object> updateCurrency(@RequestBody CurrencyRequest request) {
        return currencyService.updateCurrency(request);
    }

    //根据传入的值删除对应的货币代码
    @DeleteMapping("/deleteCurrency")
    public BaseResponse<Object> deleteCurrency(@RequestBody CurrencyRequest request) {
        return currencyService.deleteCurrency(request);
    }

    //根据传入的店铺名称获取对应的货币信息
    @GetMapping("/getCurrencyByShopName")
    public BaseResponse<Object> getCurrencyByShopName(@RequestParam String shopName) {
        return currencyService.getCurrencyByShopName(shopName);
    }

    //根据传入的货币代码和默认货币代码从rate缓存中获取对应的汇率
    @PostMapping("/getCacheData")
    public BaseResponse<Object> getCurrencyByShopId(@RequestBody CurrenciesDO request) {
        return new BaseResponse<>().CreateSuccessResponse(purchaseService.getCacheData(request));
    }

   //对currency的初始化方法，添加默认代码
    @GetMapping("/initCurrency")
    public BaseResponse<Object> initCurrency(@RequestParam String shopName) {
        return currencyService.initCurrency(shopName);
    }

    //更新默认货币的方法
    @PutMapping("/updateDefaultCurrency")
    public BaseResponse<Object> updateDefaultCurrency(@RequestBody CurrencyRequest request) {
        return currencyService.updateDefaultCurrency(request);
    }
}
