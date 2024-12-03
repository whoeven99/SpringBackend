package com.bogdatech.controller;

import com.bogdatech.entity.UsersDO;
import com.bogdatech.logic.KlaviyoService;
import com.bogdatech.model.controller.request.ProfileToListRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class KlaviyoController {
    @Autowired
    private KlaviyoService klaviyoService;

    //创建profile
    @PostMapping("/klaviyo/createProfile")
    public String createProfile(@RequestBody UsersDO usersDO){
        return klaviyoService.createProfile(usersDO);
    }

    //将profile存进list里面
    @PostMapping("/klaviyo/addProfileToKlaviyoList")
    public Boolean addProfileToKlaviyoList(@RequestBody ProfileToListRequest request){
        return klaviyoService.addProfileToKlaviyoList(request);
    }
}
