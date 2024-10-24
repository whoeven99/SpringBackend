package com.bogdatech.logic;

import com.bogdatech.model.controller.request.CurrencyRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;

import static com.bogdatech.enums.ErrorEnum.*;

@Component
public class CurrencyService {

    @Autowired
    private Connection connection;

//    @Transactional(rollbackFor = Exception.class)
    public BaseResponse addCurrency(CurrencyRequest request)  {
        // 准备SQL插入语句
        String sql = "INSERT INTO Currencies (shop_name, country_name, currency_code, rounding, exchange_rate) VALUES (?, ?, ?, ?, ?)";
        try(PreparedStatement insertStatement = connection.prepareStatement(sql);) {

            // 设置参数
            insertStatement.setString(1, request.getShopName());
            insertStatement.setString(2, request.getCountryName());
            insertStatement.setString(3, request.getCurrencyCode());
            insertStatement.setString(4, request.getRounding());
            insertStatement.setString(5, request.getExchangeRate());

            // 执行插入
            int rowsAffected = insertStatement.executeUpdate();

            if (rowsAffected > 0) {
                return new BaseResponse<>().CreateSuccessResponse("success"); //TODO success后面需要修改
            } else {
                return new BaseResponse<>().CreateErrorResponse(SQL_INSERT_ERROR);
            }
        } catch (Exception e) {
            //TODO 考虑一下事务失败后的回滚，测试的时候报错AutoCommit 模式设置为“true”时，无法调用回滚操作。
            throw new RuntimeException(e);
        }

    }

    public BaseResponse updateCurrency(CurrencyRequest request) {
        String sql = "UPDATE Currencies SET rounding = ?, exchange_rate = ? WHERE shop_name = ?";
        try (PreparedStatement updateStatement = connection.prepareStatement(sql);) {
            // 设置参数
            updateStatement.setString(1, request.getRounding());
            updateStatement.setString(2, request.getExchangeRate());
            updateStatement.setString(3, request.getShopName());

            // 执行更新
            int rowsAffected = updateStatement.executeUpdate();
            System.out.println("Update affected rows: " + rowsAffected);
            if (rowsAffected > 0) {
                return new BaseResponse<>().CreateSuccessResponse(null);
                } else {
                return new BaseResponse<>().CreateErrorResponse(SQL_UPDATE_ERROR);
            }
        }catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public BaseResponse deleteCurrency(CurrencyRequest request) {
        String sql = "DELETE FROM Currencies  WHERE shop_name = ? and currency_code = ?";
        try(PreparedStatement deleteStatement = connection.prepareStatement(sql);
                ){
            // 设置参数
            deleteStatement.setString(1, request.getShopName());
            deleteStatement.setString(2, request.getCurrencyCode());

            // 执行删除
            int rowsAffected = deleteStatement.executeUpdate();
            if (rowsAffected > 0) {
                return new BaseResponse<>().CreateSuccessResponse(null);
            } else {
                return new BaseResponse<>().CreateErrorResponse(SQL_DELETE_ERROR);
            }


        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
}
