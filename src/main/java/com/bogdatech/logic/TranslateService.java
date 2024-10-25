package com.bogdatech.logic;

import com.bogdatech.integration.TranslateApiIntegration;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;

import static com.bogdatech.enums.ErrorEnum.SQL_INSERT_ERROR;

@Component
public class TranslateService {

    @Autowired
    private TranslateApiIntegration translateApiIntegration;

    @Autowired
    private Connection connection;
    public BaseResponse translate(TranslateRequest request) {
        return new BaseResponse().CreateSuccessResponse(null);
    }

    public BaseResponse insertShopTranslateInfo(TranslateRequest request) {
        String sql = "INSERT INTO Translates (shop_name, access_token, language) VALUES (?, ?, ?)";
        try(PreparedStatement insertStatement = connection.prepareStatement(sql);) {

            // 设置参数
            insertStatement.setString(1, request.getShopName());
            insertStatement.setString(2, request.getAccessToken());
            insertStatement.setString(3, request.getLanguage());


            // 执行插入
            int rowsAffected = insertStatement.executeUpdate();

            if (rowsAffected > 0) {
                return new BaseResponse<>().CreateSuccessResponse("success");
            } else {
                return new BaseResponse<>().CreateErrorResponse(SQL_INSERT_ERROR);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
