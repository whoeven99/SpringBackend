package com.bogda.api.controller;

import com.bogda.common.controller.response.BaseResponse;
import org.springframework.web.bind.annotation.*;

import static com.bogda.api.support.DisabledProductEndpoints.error;

@RestController
@RequestMapping("/pc/userTrials")
public class PCUserTrialsController {

    @PostMapping("/startFreePlan")
    public BaseResponse<Object> startFreePlan(@RequestParam String shopName) {
        return error();
    }

    @PostMapping("/isOpenFreePlan")
    public BaseResponse<Object> isFreePlan(@RequestParam String shopName) {
        return error();
    }

    @PostMapping("/isShowFreePlan")
    public BaseResponse<Object> isShowFreePlan(@RequestParam String shopName) {
        return error();
    }

    @PostMapping("/isInFreePlanTime")
    public BaseResponse<Object> isInFreePlanTime(@RequestParam String shopName) {
        return error();
    }

    @PostMapping("/insertOrUpdateFreePlan")
    public BaseResponse<Object> insertOrUpdateFreePlan(@RequestParam String shopName) {
        return error();
    }
}
