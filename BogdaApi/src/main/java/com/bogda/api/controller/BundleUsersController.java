package com.bogda.api.controller;

import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.entity.VO.BundleInitialVO;
import com.bogda.repository.entity.BundleUserDO;
import com.bogda.service.logic.BundleApp.BundleUsersService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * Bundle 应用用户与卸载接口
 */
@RestController
@RequestMapping("/bundle/users")
public class BundleUsersController {
    @Autowired
    private BundleUsersService bundleUsersService;

    /**
     * 初始化用户：创建/复用 storefrontAccessToken 并落库，立即返回；异步拉取 shop 主信息并更新 user_tag/first_name/last_name/email。
     * 成功时 data 为 {@link BundleInitialVO}（含 storefrontAccessToken）。
     */
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
