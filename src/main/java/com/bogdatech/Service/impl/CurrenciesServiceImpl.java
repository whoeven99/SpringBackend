package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.ICurrenciesService;
import com.bogdatech.entity.DO.CurrenciesDO;
import com.bogdatech.mapper.CurrenciesMapper;
import com.bogdatech.model.controller.request.CurrencyRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.bogdatech.enums.ErrorEnum.*;
import static com.bogdatech.utils.MapUtils.getCurrencyDOS;

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
        List<Map<String, Object>> mapList = new ArrayList<>();
        for (CurrenciesDO currenciesDO : list) {
            Map<String, Object> currencyDOS = new java.util.HashMap<>(getCurrencyDOS(currenciesDO));
            mapList.add(currencyDOS);
        }
        return new BaseResponse<>().CreateSuccessResponse(mapList);
    }

    @Override
    public Map<String, Object> getCurrencyWithSymbol(CurrenciesDO request) {
        CurrenciesDO currencyByShopNameAndCurrencyCode = baseMapper.getCurrencyByShopNameAndCurrencyCode(request.getShopName(), request.getCurrencyCode());
        return new java.util.HashMap<>(getCurrencyDOS(currencyByShopNameAndCurrencyCode));
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

    @Override
    public BaseResponse<Object> updateDefaultCurrency(CurrencyRequest request) {
        // 先判断该货币是否存在，如果存在则删除
        CurrenciesDO isCurrencyExist = baseMapper.getCurrencyByShopNameAndCurrencyCode(request.getShopName(), request.getCurrencyCode());
        if (isCurrencyExist != null && baseMapper.deleteById(isCurrencyExist.getId()) <= 0) {
            return new BaseResponse<>().CreateErrorResponse(SQL_DELETE_ERROR);  // 删除失败，返回错误
        }

        // 获取当前店铺的默认货币并删除旧的默认货币
        CurrenciesDO currency = baseMapper.getPrimaryStatusByShopName(request.getShopName());
        if (currency != null && baseMapper.deleteById(currency.getId()) <= 0) {
            return new BaseResponse<>().CreateErrorResponse(SQL_DELETE_ERROR);  // 删除失败，返回错误
        }

        // 新增默认货币
        Integer result = baseMapper.insertCurrency(request.getShopName(), request.getCurrencyName(), request.getCurrencyCode(), request.getRounding(), request.getExchangeRate(), 1);
        if (result > 0) {
            CurrenciesDO primaryStatusByShopName = baseMapper.getPrimaryStatusByShopName(request.getShopName());
            return new BaseResponse<>().CreateSuccessResponse(primaryStatusByShopName);
        } else {
            return new BaseResponse<>().CreateErrorResponse(SQL_INSERT_ERROR);
        }
    }
}
