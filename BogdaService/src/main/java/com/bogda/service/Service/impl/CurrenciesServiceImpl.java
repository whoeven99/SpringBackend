package com.bogda.service.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.service.Service.ICurrenciesService;
import com.bogda.service.entity.DO.CurrenciesDO;
import com.bogda.service.mapper.CurrenciesMapper;
import com.bogda.service.controller.request.CurrencyRequest;
import com.bogda.service.controller.response.BaseResponse;
import com.bogda.service.utils.CurrencyConfig;
import com.bogda.common.utils.AppInsightsUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.bogda.common.enums.ErrorEnum.*;


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
            AppInsightsUtils.trackTrace("getCurrencyByShopName No currency found for shopName: " + shopName);
            return new BaseResponse<>().CreateErrorResponse(false);
        }
        for (CurrenciesDO currenciesDO : list) {
            Map<String, Object> currencyDOS = new java.util.HashMap<>(getCurrencyDOS(currenciesDO));
            mapList.add(currencyDOS);
        }
        return new BaseResponse<>().CreateSuccessResponse(mapList);
    }

    private Map<String, Object> getCurrencyDOS(CurrenciesDO currenciesDO) {
        Map<String, Object> map = new HashMap<>();
        map.put("currencyCode", currenciesDO.getCurrencyCode());
        map.put("currencyName", currenciesDO.getCurrencyName());
        map.put("shopName", currenciesDO.getShopName());
        map.put("id", currenciesDO.getId());
        map.put("rounding", currenciesDO.getRounding());
        map.put("exchangeRate", currenciesDO.getExchangeRate());
        map.put("primaryStatus", currenciesDO.getPrimaryStatus());

        try {
            Field field = CurrencyConfig.class.getField(currenciesDO.getCurrencyCode().toUpperCase());
            Map<String, Object> currencyInfo = (Map<String, Object>) field.get(null);
            if (currencyInfo != null) {
                map.put("symbol", currencyInfo.get("symbol"));
            } else {
                map.put("symbol", "-");
                AppInsightsUtils.trackTrace("符号错误 ： " + currenciesDO.getShopName());
            }

        } catch (NoSuchFieldException | IllegalAccessException e) {
            AppInsightsUtils.trackTrace("FatalException : " + currenciesDO.getCurrencyCode() + "currency error :  " + e.getMessage());
        }
        return map;
    }

    @Override
    public Map<String, Object> getCurrencyWithSymbol(CurrenciesDO request) {
        CurrenciesDO currencyByShopNameAndCurrencyCode = baseMapper.getCurrencyByShopNameAndCurrencyCode(request.getShopName(), request.getCurrencyCode());
        if (currencyByShopNameAndCurrencyCode == null) {
            AppInsightsUtils.trackTrace("getCurrencyWithSymbol No currency found for shopName: " + request.getShopName() + " and currencyCode: " + request.getCurrencyCode());
            return null;
        }
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

    @Override
    public List<CurrenciesDO> selectByShopName(String shopName) {
        return baseMapper.selectList(new LambdaQueryWrapper<CurrenciesDO>().eq(CurrenciesDO::getShopName, shopName));
    }
}
