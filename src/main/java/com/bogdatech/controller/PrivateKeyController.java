package com.bogdatech.controller;

import com.bogdatech.logic.PrivateKeyService;
import com.bogdatech.model.controller.request.ClickTranslateRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/privateKey")
public class PrivateKeyController {
    @Autowired
    private PrivateKeyService privateKeyService;

    @PutMapping("/translate")
    public BaseResponse<Object> translate(@RequestParam String shopName, @RequestBody ClickTranslateRequest clickTranslateRequest) {
        return privateKeyService.judgePrivateKey(shopName, clickTranslateRequest);
    }
}
