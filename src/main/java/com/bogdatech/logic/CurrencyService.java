package com.bogdatech.logic;

import com.bogdatech.model.controller.request.CurrencyRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

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
        String sql = "UPDATE Currencies SET rounding = ?, exchange_rate = ? WHERE id = ?";
        try (PreparedStatement updateStatement = connection.prepareStatement(sql);) {
            // 设置参数
            updateStatement.setString(1, request.getRounding());
            updateStatement.setString(2, request.getExchangeRate());
            updateStatement.setInt(3, request.getId());


            // 执行更新
            int rowsAffected = updateStatement.executeUpdate();
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
        String sql = "DELETE FROM Currencies  WHERE id = ?";
        try(PreparedStatement deleteStatement = connection.prepareStatement(sql);
                ){
            // 设置参数
            deleteStatement.setInt(1, request.getId());


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

    public BaseResponse getCurrencyByShopName(CurrencyRequest request) {
        String sql = "SELECT id, shop_name, country_name, currency_code, rounding, exchange_rate FROM Currencies  WHERE shop_name = ?";
        //TODO 优化sql的增删改查提取出一个通用模板
        try(PreparedStatement selectStatement = connection.prepareStatement(sql);
        ){
            // 设置参数
            selectStatement.setString(1, request.getShopName());
            ResultSet resultSet = selectStatement.executeQuery();
            List<CurrencyRequest> list = new ArrayList<>();
            while (resultSet.next()) {
                int id = resultSet.getInt("id");
                String StringName = resultSet.getString("shop_name");
                String countryName = resultSet.getString("country_name");
                String currencyCode = resultSet.getString("currency_code");
                String rounding = resultSet.getString("rounding");
                String exchangeRate = resultSet.getString("exchange_rate");
                list.add(new CurrencyRequest(id,StringName,countryName, currencyCode, rounding, exchangeRate));
            }

            resultSet.close();
            resultSet.close();
            return new BaseResponse().CreateSuccessResponse(list);



        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
