package com.bogdatech.controller;


import com.bogdatech.Service.ITranslateTextService;
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
    public UsersDO getUser(@RequestBody UsersDO userRequest) {
        return userService.getUser(userRequest);
    }

    // 添加用户
    @PostMapping("/user/add")
    public BaseResponse<Object> addUser(@RequestBody UsersDO userRequest) {

        if (userService.getUser(userRequest) == null) {
            return userService.addUser(userRequest);
        }else {
            return new BaseResponse<>().CreateErrorResponse("User already exists");
        }
//        userService.addUserAsync(userRequest);
    }

    //用户卸载应用
    @PostMapping("/user/uninstall")
    public BaseResponse<Object> uninstallApp() {
        return new BaseResponse<>().CreateSuccessResponse(userService.unInstallApp());
    }

    //用户卸载应用后48小时后清除数据
    @PostMapping("/user/cleanData")
    public BaseResponse<Object> cleanData() {
        return new BaseResponse<>().CreateSuccessResponse(userService.cleanData());
    }

    //客户可以向店主请求其数据
    @PostMapping("/user/requestData")
    public BaseResponse<Object> requestData() {
        return new BaseResponse<>().CreateSuccessResponse(userService.requestData());
    }

    //店主可以代表客户请求删除数据
    @PostMapping("/user/deleteData")
    public BaseResponse<Object> deleteData() {
        return new BaseResponse<>().CreateSuccessResponse(userService.deleteData());
    }
}