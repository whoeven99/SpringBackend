package com.bogda.api.controller;

import com.bogda.common.model.controller.request.ClickTranslateRequest;
import com.bogda.common.model.controller.response.BaseResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/privateKey")
public class PrivateKeyController {
    @PutMapping("/translate")
    public BaseResponse<Object> translate(@RequestParam String shopName, @RequestBody ClickTranslateRequest clickTranslateRequest) {
        return BaseResponse.FailedResponse("Deprecated, will reopen in future");
    }
}
