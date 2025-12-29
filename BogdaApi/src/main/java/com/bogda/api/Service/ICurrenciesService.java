package com.bogda.api.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.api.entity.DO.CurrenciesDO;
import com.bogda.api.model.controller.request.CurrencyRequest;
import com.bogda.api.model.controller.response.BaseResponse;

import java.util.List;
import java.util.Map;

public interface ICurrenciesService extends IService<CurrenciesDO> {
    BaseResponse<Object> insertCurrency(CurrenciesDO currenciesDO);

    BaseResponse<Object> updateCurrency(CurrencyRequest request);
    BaseResponse<Object> deleteCurrency(CurrencyRequest request);
    BaseResponse<Object> getCurrencyByShopName(String shopName);

    Map<String, Object> getCurrencyWithSymbol(CurrenciesDO request);

    BaseResponse<Object> initCurrency(String shopName);

    String getCurrencyCodeByPrimaryStatusAndShopName(String shopName);

    BaseResponse<Object> updateDefaultCurrency(CurrencyRequest request);

    List<CurrenciesDO> selectByShopName(String shopName);
}
