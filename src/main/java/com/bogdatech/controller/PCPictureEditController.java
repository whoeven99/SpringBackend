package com.bogdatech.controller;

import com.bogdatech.integration.AidgeIntegration;
import com.bogdatech.model.controller.request.SignRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.model.controller.response.SignResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/pc/edit")
public class PCPictureEditController {

    @PostMapping("/getSignResponse")
    public BaseResponse<Object> getSignResponse(@RequestBody SignRequest signRequest) {
        SignResponse signResponse = AidgeIntegration.getSignResponse(signRequest.getParams(), signRequest.getApi());
        return new BaseResponse<>().CreateSuccessResponse(signResponse);
    }
}
