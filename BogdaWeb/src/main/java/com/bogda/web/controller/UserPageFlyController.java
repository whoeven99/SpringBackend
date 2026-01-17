package com.bogda.web.controller;

import com.bogda.service.entity.DO.UserPageFlyDO;
import com.bogda.service.logic.UserPageFlyService;
import com.bogda.service.controller.response.BaseResponse;
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
        return userPageFlyService.editTranslatedData(shopName, userPageFlyDO);
    }
}
