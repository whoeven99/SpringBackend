package com.bogdatech.controller;

import com.bogdatech.logic.UserService;
import com.bogdatech.model.controller.request.UserRequest;
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

    @GetMapping("/user/get")
    public void getUser() {
        userService.getUser();
    }

    @PostMapping("/user/add")
    public BaseResponse<Object> addUser(@RequestBody UserRequest userRequest) {
        return userService.addUser(userRequest);
    }

}