package com.bogdatech.controller;

import com.bogdatech.logic.UserTypeTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/userTypeToken")
public class UserTypeTokenController {
    private final UserTypeTokenService userTypeTokenService;

    @Autowired
    public UserTypeTokenController(UserTypeTokenService userTypeTokenService) {
        this.userTypeTokenService = userTypeTokenService;
    }


}
