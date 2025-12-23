package com.bogdatech.controller;

import com.bogdatech.logic.UserPrivateService;
import com.bogdatech.model.controller.request.UserPrivateRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/private")
public class UserPrivateController {
    @Autowired
    private UserPrivateService userPrivateService;

    @PostMapping("/saveUserData")
    public BaseResponse<Object> saveUserData(@RequestBody UserPrivateRequest userPrivateRequest) {
        return userPrivateService.saveOrUpdateUserData(userPrivateRequest);
    }

    @PostMapping("/getUserData")
    public BaseResponse<Object> getUserData(@RequestBody UserPrivateRequest userPrivateRequest) {
        return userPrivateService.getUserData(userPrivateRequest);
    }

    @PostMapping("/updateUsedData")
    public void updateUsedCharsByShopName(@RequestBody UserPrivateRequest userPrivateRequest) {
        userPrivateService.updateUsedCharsByShopName(userPrivateRequest.getShopName(), userPrivateRequest.getAmount());
    }

    @PutMapping("/deleteUserData")
    public BaseResponse<Object> deleteUserData(@RequestBody UserPrivateRequest userPrivateRequest) {
        Boolean b = userPrivateService.deleteUserData(userPrivateRequest.getShopName());
        return new BaseResponse<>().CreateSuccessResponse(b);
    }
}
