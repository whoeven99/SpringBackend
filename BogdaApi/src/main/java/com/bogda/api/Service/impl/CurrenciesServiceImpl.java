package com.bogda.api.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.api.Service.ICurrenciesService;
import com.bogda.api.entity.DO.CurrenciesDO;
import com.bogda.api.mapper.CurrenciesMapper;
import com.bogda.api.model.controller.request.CurrencyRequest;
import com.bogda.api.model.controller.response.BaseResponse;
import com.bogda.api.utils.ShopifyUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.bogda.common.enums.ErrorEnum.*;
import static com.bogda.common.utils.CaseSensitiveUtils.appInsights;

@Service
@Transactional
public class CurrenciesServiceImpl extends ServiceImpl<CurrenciesMapper, CurrenciesDO> implements ICurrenciesService {


    @Override
    public BaseResponse<Object> insertCurrency(CurrenciesDO request) {
        // 准备SQL插入语句
        if (request.getCurrencyCode() == null) {
            return new BaseResponse<>().CreateErrorResponse("CURRENCY_CODE_NOT_NULL");
        }
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
        if (list.length == 0){
            appInsights.trackTrace("getCurrencyByShopName No currency found for shopName: " + shopName);
            return new BaseResponse<>().CreateErrorResponse(false);
        }
        for (CurrenciesDO currenciesDO : list) {
            Map<String, Object> currencyDOS = new java.util.HashMap<>(ShopifyUtils.getCurrencyDOS(currenciesDO));
            mapList.add(currencyDOS);
        }
        return new BaseResponse<>().CreateSuccessResponse(mapList);
    }

    @Override
    public Map<String, Object> getCurrencyWithSymbol(CurrenciesDO request) {
        CurrenciesDO currencyByShopNameAndCurrencyCode = baseMapper.getCurrencyByShopNameAndCurrencyCode(request.getShopName(), request.getCurrencyCode());
        if (currencyByShopNameAndCurrencyCode == null) {
            appInsights.trackTrace("getCurrencyWithSymbol No currency found for shopName: " + request.getShopName() + " and currencyCode: " + request.getCurrencyCode());
            return null;
        }
        return new java.util.HashMap<>(ShopifyUtils.getCurrencyDOS(currencyByShopNameAndCurrencyCode));
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

    @Override
    public List<CurrenciesDO> selectByShopName(String shopName) {
        return baseMapper.selectList(new LambdaQueryWrapper<CurrenciesDO>().eq(CurrenciesDO::getShopName, shopName));
    }
}
