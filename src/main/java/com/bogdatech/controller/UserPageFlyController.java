package com.bogdatech.controller;

import com.bogdatech.entity.DO.UserPageFlyDO;
import com.bogdatech.logic.UserPageFlyService;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/userPageFly")
public class UserPageFlyController {
    @Autowired
    private UserPageFlyService userPageFlyService;

    // 根据前端的shopName和languageCode读取数据api
    @PostMapping("/readTranslatedText")
    public BaseResponse<Object> readTranslatedText(@RequestParam String shopName, @RequestParam String languageCode) {
        return userPageFlyService.readTranslatedText(shopName, languageCode);
    }

    // 根据前端的传入数据更新或插入对应数据
    @PostMapping("/editTranslatedData")
    public BaseResponse<Object> editTranslatedData(@RequestParam String shopName, @RequestBody List<UserPageFlyDO> userPageFlyDO) {
        System.out.println("userPageFlyDO " + userPageFlyDO);
        return userPageFlyService.editTranslatedData(shopName, userPageFlyDO);
    }
}
