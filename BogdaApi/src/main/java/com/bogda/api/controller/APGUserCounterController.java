package com.bogda.api.controller;

import com.bogda.common.controller.response.BaseResponse;
import org.springframework.web.bind.annotation.*;

import static com.bogda.api.support.DisabledProductEndpoints.error;

@RestController
@RequestMapping("/apg/userCounter")
public class APGUserCounterController {

    @GetMapping("/initUserCounter")
    public BaseResponse<Object> initUserCounter(@RequestParam String shopName) {
        return error();
    }

    @PostMapping("/getUserCounter")
    public BaseResponse<Object> getUserCounter(@RequestParam String shopName) {
        return error();
    }

    @PutMapping("/updateUserToken")
    public BaseResponse<Object> updateUserToken(@RequestParam String shopName, @RequestParam Integer token) {
        return error();
    }

    @PostMapping("/sendAPGPurchaseEmail")
    public BaseResponse<Object> sendAPGPurchaseEmail(
            @RequestParam String shopName,
            @RequestParam Integer token,
            @RequestParam Double amount) {
        return error();
    }
}
