package com.bogdatech.controller;

import com.bogdatech.entity.DO.UsersDO;
import com.bogdatech.logic.KlaviyoService;
import com.bogdatech.model.controller.request.ProfileToListRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/klaviyo")
public class KlaviyoController {
    // 下面功能目前没用了，待删除
    private final KlaviyoService klaviyoService;
    @Autowired
    public KlaviyoController(KlaviyoService klaviyoService) {
        this.klaviyoService = klaviyoService;
    }
    //创建profile
    @PostMapping("/createProfile")
    public String createProfile(@RequestBody UsersDO usersDO){
        return klaviyoService.createProfile(usersDO);
    }

    //将profile存进list里面
    @PostMapping("/addProfileToKlaviyoList")
    public Boolean addProfileToKlaviyoList(@RequestBody ProfileToListRequest request){
        return klaviyoService.addProfileToKlaviyoList(request);
    }
}
