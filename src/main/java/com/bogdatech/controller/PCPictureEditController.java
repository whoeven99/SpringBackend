package com.bogdatech.controller;

import com.bogdatech.integration.AidgeIntegration;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.model.controller.response.SignResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/pc/edit")
public class PCPictureEditController {

    @PostMapping("/getSignResponse")
    public BaseResponse<Object> getSignResponse(@RequestParam String api, @RequestBody Map<String, String> params) {
        SignResponse signResponse = AidgeIntegration.getSignResponse(params, api);
        return new BaseResponse<>().CreateSuccessResponse(signResponse);
    }
}
