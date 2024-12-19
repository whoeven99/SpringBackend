package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.CurrenciesDO;
import com.bogdatech.model.controller.request.CurrencyRequest;
import com.bogdatech.model.controller.response.BaseResponse;

import java.util.Map;

public interface ICurrenciesService extends IService<CurrenciesDO> {
    public BaseResponse<Object> insertCurrency(CurrenciesDO currenciesDO);

    public BaseResponse<Object> updateCurrency(CurrencyRequest request);
    public BaseResponse<Object> deleteCurrency(CurrencyRequest request);
    public BaseResponse<Object> getCurrencyByShopName(String shopName);

    Map<String, Object> getCurrencyWithSymbol(CurrenciesDO request);

    BaseResponse<Object> initCurrency(String shopName);

    String getCurrencyCodeByPrimaryStatusAndShopName(String shopName);
}
