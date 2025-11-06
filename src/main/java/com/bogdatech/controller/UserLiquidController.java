package com.bogdatech.controller;

import com.bogdatech.entity.DO.UserLiquidDO;
import com.bogdatech.logic.UserLiquidService;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/liquid")
public class UserLiquidController {
    @Autowired
    private UserLiquidService userLiquidService;

    // 读取Liquid数据
    @PostMapping("/selectShopNameLiquidData")
    public BaseResponse<Object> selectShopNameLiquidData(@RequestParam String shopName, @RequestBody UserLiquidDO userLiquidDO) {
        return userLiquidService.selectShopNameLiquidData(shopName, userLiquidDO);
    }

    // 写入Liquid数据
    @PostMapping("/insertShopNameLiquidData")
    public BaseResponse<Object> insertShopNameLiquidData(@RequestParam String shopName, @RequestBody UserLiquidDO userLiquidDO) {
        return userLiquidService.insertShopNameLiquidData(shopName, userLiquidDO);
    }
}
