package com.bogda.web.controller;

import com.bogda.service.Service.ICurrenciesService;
import com.bogda.common.entity.DO.CurrenciesDO;
import com.bogda.service.logic.PurchaseService;
import com.bogda.common.controller.request.CurrencyRequest;
import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.utils.AppInsightsUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/currency")
public class CurrencyController {
    @Autowired
    private ICurrenciesService currencyService;
    @Autowired
    private PurchaseService purchaseService;

    //根据传入的货币代码插入货币信息
    @PostMapping("/insertCurrency")
    public BaseResponse<Object> addCurrency(@RequestBody CurrenciesDO currenciesDO) {
        AppInsightsUtils.trackTrace("insertCurrency currenciesDO: " + currenciesDO);
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
        Map<String, Object> cacheData = purchaseService.getCacheData(request);
        if (cacheData != null) {
            return new BaseResponse<>().CreateSuccessResponse(cacheData);
        } else {
            return new BaseResponse<>().CreateErrorResponse(false);
        }
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
