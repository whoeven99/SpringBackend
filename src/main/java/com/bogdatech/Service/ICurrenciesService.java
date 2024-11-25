package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.CurrenciesDO;
import com.bogdatech.model.controller.request.CurrencyRequest;
import com.bogdatech.model.controller.response.BaseResponse;

public interface ICurrenciesService extends IService<CurrenciesDO> {
    public BaseResponse<Object> insertCurrency(CurrencyRequest request);

    public BaseResponse<Object> updateCurrency(CurrencyRequest request);
    public BaseResponse<Object> deleteCurrency(CurrencyRequest request);
    public BaseResponse<Object> getCurrencyByShopName(CurrencyRequest request);
}
