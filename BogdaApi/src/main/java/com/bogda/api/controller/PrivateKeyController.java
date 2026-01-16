package com.bogda.api.controller;

import com.bogda.service.controller.request.ClickTranslateRequest;
import com.bogda.service.controller.response.BaseResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/privateKey")
public class PrivateKeyController {
    @PutMapping("/translate")
    public BaseResponse<Object> translate(@RequestParam String shopName, @RequestBody ClickTranslateRequest clickTranslateRequest) {
        return BaseResponse.FailedResponse("Deprecated, will reopen in future");
    }
}
