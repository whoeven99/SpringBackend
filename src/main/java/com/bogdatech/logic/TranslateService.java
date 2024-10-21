package com.bogdatech.logic;

import com.bogdatech.integration.TranslateApiIntegration;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TranslateService {

    @Autowired
    private TranslateApiIntegration translateApiIntegration;

    public BaseResponse translate(TranslateRequest request) {
        return new BaseResponse().CreateSuccessResponse(null);
    }
}
