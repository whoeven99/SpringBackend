package com.bogda.web.controller;

import com.bogda.common.controller.response.BaseResponse;
import com.bogda.service.logic.BundleApp.BundleUsersService;
import com.bogda.repository.entity.BundleUserDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bundle/users")
public class BundleUsersController {
    @Autowired
    private BundleUsersService bundleUsersService;

    // 初始化用户数据
    @PostMapping("/initUser")
    public BaseResponse<Object> initUser(@RequestParam String shopName, @RequestBody BundleUserDO bundleUserDO) {
        return bundleUsersService.initUser(shopName, bundleUserDO);
    }
}
