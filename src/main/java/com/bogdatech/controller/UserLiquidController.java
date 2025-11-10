package com.bogdatech.controller;

import com.bogdatech.entity.VO.InsertLiquidVO;
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
    public BaseResponse<Object> selectShopNameLiquidData(@RequestParam String shopName) {
        return userLiquidService.selectShopNameLiquidData(shopName);
    }

    // 写入Liquid数据
    @PostMapping("/insertShopNameLiquidData")
    public BaseResponse<Object> insertShopNameLiquidData(@RequestParam String shopName, @RequestBody InsertLiquidVO insertLiquidVO) {
        return userLiquidService.insertShopNameLiquidData(shopName, insertLiquidVO);
    }

    // 获取用户语言对应下的所有数据，解析后返回给前端
    @PostMapping("/parseLiquidDataByShopNameAndLanguage")
    public BaseResponse<Object> parseLiquidDataByShopNameAndLanguage(@RequestParam String shopName, @RequestParam String languageCode) {
        return userLiquidService.parseLiquidDataByShopNameAndLanguage(shopName, languageCode);
    }
}
