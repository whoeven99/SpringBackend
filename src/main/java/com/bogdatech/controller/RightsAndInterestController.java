package com.bogdatech.controller;

import com.bogdatech.Service.IRightsAndInterestsService;
import com.bogdatech.model.controller.request.UserRAIRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/rightsAndInterests")
public class RightsAndInterestController {
    @Autowired
    private IRightsAndInterestsService rightsAndInterestsService;

    // 读取所有权益
    @GetMapping("/readRightsAndInterests")
    public BaseResponse<Object> readRightsAndInterests() {
        return rightsAndInterestsService.readRightsAndInterests();
    }

    //创建用户对应的权益
    @PostMapping("/createRightsAndInterests")
    public BaseResponse<Object> createOrUpdateRightsAndInterests(@RequestBody UserRAIRequest userRAIRequest) {
        return rightsAndInterestsService.createOrUpdateRightsAndInterests(userRAIRequest);
    }
}
