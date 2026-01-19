package com.bogda.web.controller;

import com.bogda.common.entity.DO.PCUsersDO;
import com.bogda.common.entity.VO.AddCharsVO;
import com.bogda.common.entity.VO.TranslationCharsVO;
import com.bogda.service.logic.PCApp.PCUsersService;
import com.bogda.common.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pcUsers")
public class PCUsersController {
    @Autowired
    private PCUsersService pcUsersService;

    // 用户初始化
    @PostMapping("/initUser")
    public void initUser(@RequestParam String shopName, @RequestBody PCUsersDO pcUsersDO) {
        pcUsersDO.setShopName(shopName);
        pcUsersService.initUser(shopName, pcUsersDO);
    }

    // 添加额度
    @PutMapping("/addPurchasePoints")
    public BaseResponse<Object> addPurchasePoints(@RequestParam String shopName, @RequestBody AddCharsVO addCharsVO) {
        return pcUsersService.addPurchasePoints(shopName, addCharsVO.getChars(), addCharsVO.getGid());
    }

    // 查询额度
    @PostMapping("/getPurchasePoints")
    public BaseResponse<Object> getPurchasePoints(@RequestParam String shopName) {
        return pcUsersService.getPurchasePoints(shopName);
    }

    // 用户卸载
    @PostMapping("/uninstall")
    public BaseResponse<Object> uninstall(@RequestParam String shopName) {
        return pcUsersService.uninstall(shopName);
    }

    /**
     * 订阅付费计划后，后端判断是否是免费计划，是的话，不添加额度；不是的话，添加额度
     */
    @PostMapping("/addCharsByShopNameAfterSubscribe")
    public BaseResponse<Object> addCharsByShopNameAfterSubscribe(@RequestParam String shopName, @RequestBody TranslationCharsVO translationCharsVO) {
        return pcUsersService.addCharsByShopNameAfterSubscribe(shopName, translationCharsVO);
    }
}
