package com.bogdatech.controller;


import com.bogdatech.Service.ITranslateTextService;
import com.bogdatech.entity.TranslateTextDO;
import com.bogdatech.entity.UsersDO;
import com.bogdatech.logic.UserService;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private ITranslateTextService translateService;

    @GetMapping("/user/get")
    public void getUser() {
        userService.getUser();
    }

    // 添加用户
    @PostMapping("/user/add")
    public BaseResponse<Object> addUser(@RequestBody UsersDO userRequest) {
        return userService.addUser(userRequest);
    }

    @GetMapping("/user/test")
    public void test() {
        TranslateTextDO request = new TranslateTextDO();
        request.setTargetText("1");
        request.setTargetCode("1");
        request.setResourceId("1");
        request.setDigest("1");
        request.setSourceCode("1");
        request.setShopName("1");
        request.setTextKey("1");
        request.setSourceText("1");
        request.setTextType("1");
        translateService.insertTranslateText(request);
    }
}