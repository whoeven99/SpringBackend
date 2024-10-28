package com.bogdatech.logic;


import com.alibaba.fastjson.JSONObject;
import com.bogdatech.entity.TranslatesDO;
import com.bogdatech.integration.TranslateApiIntegration;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.repository.JdbcRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;

import static com.bogdatech.enums.ErrorEnum.TRANSLATE_ERROR;

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
        jdbcRepository.updateTranslateStatus(request.getId(), 0);
    }

    public BaseResponse userBDTranslateProduct(JSONObject object){
        //用循环将object中名字为title的翻译
        for (String key : object.keySet()) {
            if ("title".equals(key)) {
                String title = object.getString(key);
                String result = translateApiIntegration.baiDuTranslate(new TranslateRequest(0,null,null, "en", "zh",title));
                if (result!= null) {
                    object.put(key, result);
                } else {
                    return new BaseResponse<>().CreateErrorResponse(TRANSLATE_ERROR);
                }
            }
        }
        return new BaseResponse<>().CreateSuccessResponse(object);
    }

}

