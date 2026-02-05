package com.bogda.api.controller;

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

    // 用户卸载：uninstall_at 设为 UTC 当前时间，该 shop 下所有优惠 status/is_deleted 改为 false，Cosmos 对应文档删除
    @PostMapping("/uninstall")
    public BaseResponse<Object> uninstall(@RequestParam String shopName) {
        return bundleUsersService.uninstall(shopName);
    }
}
