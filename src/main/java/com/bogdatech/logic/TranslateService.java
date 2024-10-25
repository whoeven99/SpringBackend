package com.bogdatech.logic;


import com.bogdatech.integration.TranslateApiIntegration;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;

import static com.bogdatech.enums.ErrorEnum.SQL_INSERT_ERROR;

@Component
public class TranslateService {

    @Autowired
    private TranslateApiIntegration translateApiIntegration;

    @Autowired
    private Connection connection;

    @Value("${google.api.key}")
    private String apiKey;

    // 构建URL
    String url = "https://translation.googleapis.com/language/translate/v2?key=" + apiKey +
            "&q=" + "hi" +
            "&source=zh-CN&target=en";
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

    public BaseResponse translateTest() {
        String result = null;
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            // 创建HttpGet请求
            HttpGet httpGet = new HttpGet(url);

            // 执行请求
            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                if (response.getStatusLine().getStatusCode() == 200) {
                    // 获取响应实体并转换为字符串
                    result = EntityUtils.toString(response.getEntity());
                    System.out.println("Translation Result: " + result);
                } else {
                    System.out.println("Failed to translate text.");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new BaseResponse<>().CreateSuccessResponse(result);
    }
    }

