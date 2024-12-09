package com.bogdatech.controller;


import com.bogdatech.Service.ITranslateTextService;
import com.bogdatech.entity.UsersDO;
import com.bogdatech.logic.UserService;
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
    public void addUser(@RequestBody UsersDO userRequest) {

//        if (userService.getUser(userRequest) == null) {
//            return userService.addUser(userRequest);
//        }else {
//            return new BaseResponse<>().CreateErrorResponse("User already exists");
//        }
        userService.addUserAsync(userRequest);
    }


}