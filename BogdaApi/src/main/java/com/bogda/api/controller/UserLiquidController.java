package com.bogda.api.controller;

import com.bogda.api.entity.DO.UserLiquidDO;
import com.bogda.api.logic.UserLiquidService;
import com.bogda.api.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/liquid")
public class UserLiquidController {
    @Autowired
    private UserLiquidService userLiquidService;

    // 读取Liquid数据
    @PostMapping("/selectShopNameLiquidData")
    public BaseResponse<Object> selectShopNameLiquidData(@RequestParam String shopName) {
        return userLiquidService.selectShopNameLiquidData(shopName);
    }

    // 写入Liquid数据
    @PostMapping("/insertShopNameLiquidData")
    public BaseResponse<Object> insertShopNameLiquidData(@RequestParam String shopName, @RequestBody UserLiquidDO userLiquidDO) {
        return userLiquidService.insertShopNameLiquidData(shopName, userLiquidDO);
    }

    // 根据id和shopName修改replacementMethod
    @PostMapping("/updateLiquidReplacementMethod")
    public BaseResponse<Object> updateLiquidReplacementMethod(@RequestParam String shopName, @RequestParam Integer id) {
        return userLiquidService.updateLiquidReplacementMethod(shopName, id);
    }

    // 获取用户语言对应下的所有数据，解析后返回给前端
    @PostMapping("/parseLiquidDataByShopNameAndLanguage")
    public BaseResponse<Object> parseLiquidDataByShopNameAndLanguage(@RequestParam String shopName, @RequestParam String languageCode) {
        return userLiquidService.parseLiquidDataByShopNameAndLanguage(shopName, languageCode);
    }

    // 根据id批量删除liquid数据
    @PostMapping("/deleteLiquidDataByIds")
    public BaseResponse<Object> deleteLiquidDataByIds(@RequestParam String shopName, @RequestBody List<Integer> ids) {
        return userLiquidService.deleteLiquidDataByIds(shopName, ids);
    }
}
