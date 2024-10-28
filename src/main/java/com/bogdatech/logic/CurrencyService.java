package com.bogdatech.logic;

import com.bogdatech.integration.AzureSQLIntegration;
import com.bogdatech.model.controller.request.CurrencyRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.List;

import static com.bogdatech.enums.ErrorEnum.SQL_DELETE_ERROR;
import static com.bogdatech.enums.ErrorEnum.SQL_INSERT_ERROR;

@Component
public class CurrencyService {

    @Autowired
    private Connection connection;

    @Autowired
    private AzureSQLIntegration azureSQLIntegration;

//    @Transactional(rollbackFor = Exception.class)
    public BaseResponse insertCurrency(CurrencyRequest request)  {
        // 准备SQL插入语句
        String sql = "INSERT INTO Currencies (shop_name, country_name, currency_code, rounding, exchange_rate) VALUES (?, ?, ?, ?, ?)";
        Object[] info = {request.getShopName(), request.getCountryName(), request.getCurrencyCode(), request.getRounding(), request.getExchangeRate()};
        int result = azureSQLIntegration.CUDInfo(info, sql);
        if (result > 0) {
            return new BaseResponse().CreateSuccessResponse(result);
        }
        return new BaseResponse().CreateErrorResponse(SQL_INSERT_ERROR);
    }

    public BaseResponse updateCurrency(CurrencyRequest request) {
        String sql = "UPDATE Currencies SET rounding = ?, exchange_rate = ? WHERE id = ?";
        Object[] info = {request.getRounding(), request.getExchangeRate(), request.getId()};
        int result = azureSQLIntegration.CUDInfo(info, sql);
        if (result > 0) {
            return new BaseResponse().CreateSuccessResponse(result);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_DELETE_ERROR);

    }

    public BaseResponse deleteCurrency(CurrencyRequest request) {
        String sql = "DELETE FROM Currencies  WHERE id = ?";
        Object[] info = {request.getId()};
        int result = azureSQLIntegration.CUDInfo(info, sql);
        if (result > 0) {
            return new BaseResponse().CreateSuccessResponse(result);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_DELETE_ERROR);

    }

    public BaseResponse getCurrencyByShopName(CurrencyRequest request) {
        String sql = "SELECT id, shop_name, country_name, currency_code, rounding, exchange_rate FROM Currencies  WHERE shop_name = ?";
        Object[] info = {request.getShopName()};
        List<CurrencyRequest> list = azureSQLIntegration.readInfo(info, sql, CurrencyRequest.class);
        return new BaseResponse<>().CreateSuccessResponse(list);
    }

    public BaseResponse test(CurrencyRequest request) {
        String sql = "SELECT id, shop_name, country_name, currency_code, rounding, exchange_rate FROM Currencies  WHERE shop_name = ?";
        Object[] info = {request.getShopName()};
        List<CurrencyRequest> list = azureSQLIntegration.readInfo(info, sql, CurrencyRequest.class);
        return new BaseResponse<>().CreateSuccessResponse(list);
    }
}
