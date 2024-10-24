package com.bogdatech.controller;

import com.bogdatech.logic.TranslateService;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
public class TranslateController {

    @Autowired
    private TranslateService translateService;

    @PostMapping("/translate")
    public BaseResponse translate(@RequestBody TranslateRequest request) {
        return translateService.translate(request);
    }
}
