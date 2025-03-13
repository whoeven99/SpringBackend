package com.bogdatech.controller;

import com.bogdatech.logic.UserPrivateService;
import com.bogdatech.model.controller.request.UserPrivateRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("private")
public class UserPrivateController {

    private final UserPrivateService userPrivateService;

    @Autowired
    public UserPrivateController(UserPrivateService userPrivateService) {
        this.userPrivateService = userPrivateService;
    }

    @PostMapping("saveUserData")
    public BaseResponse<Object> saveUserData(@RequestBody UserPrivateRequest userPrivateRequest) {
        return userPrivateService.saveOrUpdateUserData(userPrivateRequest);
    }

    @PostMapping("getUserData")
    public BaseResponse<Object> getUserData(@RequestBody UserPrivateRequest userPrivateRequest) {
        return userPrivateService.getUserData(userPrivateRequest);
    }

    @PostMapping("updateUsedData")
    public void updateUsedCharsByShopName(@RequestBody UserPrivateRequest userPrivateRequest) {
        userPrivateService.updateUsedCharsByShopName(userPrivateRequest.getShopName(), userPrivateRequest.getAmount());
    }
}
