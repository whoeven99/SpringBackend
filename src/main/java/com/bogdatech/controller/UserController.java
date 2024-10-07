package com.bogdatech.controller;

import com.bogdatech.logic.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
    public void addUser() {
        userService.addUser();
    }
}