package com.bogda.web.controller;

import com.bogda.service.logic.UserPrivateService;
import com.bogda.service.controller.request.UserPrivateRequest;
import com.bogda.service.controller.response.BaseResponse;
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
