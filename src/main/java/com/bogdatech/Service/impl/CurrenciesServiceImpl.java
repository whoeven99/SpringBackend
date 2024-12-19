package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.ICurrenciesService;
import com.bogdatech.config.CurrencyConfig;
import com.bogdatech.entity.CurrenciesDO;
import com.bogdatech.mapper.CurrenciesMapper;
import com.bogdatech.model.controller.request.CurrencyRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static com.bogdatech.enums.ErrorEnum.*;

@Service
@Transactional
public class CurrenciesServiceImpl extends ServiceImpl<CurrenciesMapper, CurrenciesDO> implements ICurrenciesService {


    @Override
    public BaseResponse<Object> insertCurrency(CurrenciesDO request) {
        // 准备SQL插入语句
        if (baseMapper.insertCurrency(request.getShopName(), request.getCurrencyName(),
                request.getCurrencyCode(), request.getRounding(), request.getExchangeRate(), request.getPrimaryStatus()) > 0) {
            return new BaseResponse<>().CreateSuccessResponse(baseMapper.getCurrencyByShopNameAndCurrencyCode(request.getShopName(), request.getCurrencyCode()));
        } else {
            return new BaseResponse<>().CreateErrorResponse(SQL_INSERT_ERROR);
        }
    }

    @Override
    public BaseResponse<Object> updateCurrency(CurrencyRequest request) {
        int result = baseMapper.updateCurrency(request.getId(), request.getRounding(), request.getExchangeRate());
        if (result > 0) {
            return new BaseResponse<>().CreateSuccessResponse(request);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_UPDATE_ERROR);

    }

    @Override
    public BaseResponse<Object> deleteCurrency(CurrencyRequest request) {
        int result = baseMapper.deleteCurrency(request.getId());
        if (result > 0) {
            return new BaseResponse<>().CreateSuccessResponse(request.getId());
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_DELETE_ERROR);
    }

    @Override
    public BaseResponse<Object> getCurrencyByShopName(String shopName) {
        CurrenciesDO[] list = baseMapper.getCurrencyByShopName(shopName);
        return new BaseResponse<>().CreateSuccessResponse(list);
    }

    @Override
    public Map<String, Object> getCurrencyWithSymbol(CurrenciesDO request) {
        CurrenciesDO currencyByShopNameAndCurrencyCode = baseMapper.getCurrencyByShopNameAndCurrencyCode(request.getShopName(), request.getCurrencyCode());
        Map<String, Object> map = new HashMap<>();
        map.put("id", currencyByShopNameAndCurrencyCode.getId());
        map.put("shopName", currencyByShopNameAndCurrencyCode.getShopName());
        map.put("currencyCode", currencyByShopNameAndCurrencyCode.getCurrencyCode());
        map.put("currencyName", currencyByShopNameAndCurrencyCode.getCurrencyName());
        map.put("rounding", currencyByShopNameAndCurrencyCode.getRounding());
        map.put("exchangeRate", currencyByShopNameAndCurrencyCode.getExchangeRate());
        map.put("primaryStatus", currencyByShopNameAndCurrencyCode.getPrimaryStatus());
        try {
            Field field = CurrencyConfig.class.getField(request.getCurrencyCode().toUpperCase());
            Map<String, Object> currencyInfo = (Map<String, Object>) field.get(null);
            map.put("symbol", currencyInfo.get("symbol"));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return map;
    }

    @Override
    public BaseResponse<Object> initCurrency(String shopName) {
        CurrenciesDO currency = baseMapper.getPrimaryStatusByShopName(shopName);
        if (currency != null) {
            return new BaseResponse<>().CreateSuccessResponse(currency);
        } else {
            return new BaseResponse<>().CreateSuccessResponse("");
        }
    }

    @Override
    public String getCurrencyCodeByPrimaryStatusAndShopName(String shopName) {
        return baseMapper.getCurrencyCodeByPrimaryStatusAndShopName(shopName);
    }
}
