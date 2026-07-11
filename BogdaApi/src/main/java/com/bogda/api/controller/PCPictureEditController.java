package com.bogda.api.controller;

import com.bogda.common.controller.request.SignRequest;
import com.bogda.common.controller.response.AidgeResponse;
import org.springframework.web.bind.annotation.*;

import static com.bogda.api.support.DisabledProductEndpoints.aidgeError;

@RestController
@RequestMapping("/pc/edit")
public class PCPictureEditController {

    @PostMapping("/getSignResponse")
    public AidgeResponse<Object> getSignResponse(@RequestBody SignRequest signRequest) {
        return aidgeError();
    }
}
