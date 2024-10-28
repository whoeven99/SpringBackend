package com.bogdatech.logic;


import com.bogdatech.entity.TranslatesDO;
import com.bogdatech.repository.JdbcRepository;
import com.bogdatech.integration.TranslateApiIntegration;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.bogdatech.enums.ErrorEnum.*;

@Component
@EnableAsync
public class TranslateService {

    @Autowired
    private TranslateApiIntegration translateApiIntegration;

    @Autowired
    private JdbcRepository jdbcRepository;

    // 构建URL

    public BaseResponse translate(TranslatesDO request) {
        return new BaseResponse().CreateSuccessResponse(null);
    }

    public BaseResponse baiDuTranslate(TranslateRequest request) {
        String result = translateApiIntegration.baiDuTranslate(request);
        if (result != null) {
            return new BaseResponse().CreateSuccessResponse(result);
        }
        return new BaseResponse<>().CreateErrorResponse(TRANSLATE_ERROR);
    }

    public BaseResponse googleTranslate(TranslateRequest request) {
        String result = translateApiIntegration.googleTranslate(request);
        if (result != null) {
            return new BaseResponse().CreateSuccessResponse(result);
        }
        return new BaseResponse<>().CreateErrorResponse(TRANSLATE_ERROR);
    }

    public BaseResponse insertShopTranslateInfo(TranslateRequest request) {
        String sql = "INSERT INTO Translates (shop_name, access_token, source, target) VALUES (?, ?, ?, ?)";
        Object[] info = {request.getShopName(), request.getAccessToken(), request.getSource(), request.getTarget()};
        int result = jdbcRepository.CUDInfo(info, sql);
        if (result > 0) {
            return new BaseResponse().CreateSuccessResponse(result);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_INSERT_ERROR);
    }

    public List<TranslatesDO> readTranslateInfo(int status){
        String sql = "SELECT id,source,target,shop_name,status,create_at,update_at FROM Translates WHERE status = ?";
        Object[] info = {status};
        List<TranslatesDO> list =  jdbcRepository.readInfo(info, sql, TranslatesDO.class);
        return list;
    }

    public int updateTranslateStatus(int id, int status){
        String sql = "UPDATE Translates SET status = ? WHERE id = ?";
        Object[] info = {status, id};
        int result = jdbcRepository.CUDInfo(info, sql);
        return result;
    }

    @Async
    public void test(TranslatesDO request){
        System.out.println("我要翻译了" + Thread.currentThread().getName());
        //睡眠1分钟
        try {
            Thread.sleep(1000 * 60 * 1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("翻译完成" + Thread.currentThread().getName());
        //更新状态
        updateTranslateStatus(request.getId(), 0);
    }

    public BaseResponse readInfoByShopName(TranslateRequest request) {
        String sql = "SELECT id,source,target,shop_name,status,create_at,update_at FROM Translates WHERE shop_name = ?";
        Object[] info = {request.getShopName()};
        List<TranslatesDO> translatesDOS = jdbcRepository.readInfo(info, sql, TranslatesDO.class);
        if (translatesDOS.size() > 0){
            return new BaseResponse().CreateSuccessResponse(translatesDOS);
        }
           return new BaseResponse<>().CreateErrorResponse(SQL_SELECT_ERROR);
    }
}

