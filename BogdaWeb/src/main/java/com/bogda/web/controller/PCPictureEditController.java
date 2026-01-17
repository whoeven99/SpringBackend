package com.bogda.web.controller;

import com.bogda.service.integration.AidgeIntegration;
import com.bogda.service.controller.request.SignRequest;
import com.bogda.service.controller.response.AidgeResponse;
import com.bogda.service.controller.response.SignResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pc/edit")
public class PCPictureEditController {

    @PostMapping("/getSignResponse")
    public AidgeResponse<Object> getSignResponse(@RequestBody SignRequest signRequest) {
        SignResponse signResponse = AidgeIntegration.getSignResponse(signRequest.getParams(), signRequest.getApi());
        return new AidgeResponse<>().CreateSuccessResponse(signResponse);
    }
}
