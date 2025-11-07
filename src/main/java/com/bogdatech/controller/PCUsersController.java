package com.bogdatech.controller;

import com.bogdatech.entity.DO.PCUsersDO;
import com.bogdatech.entity.VO.AddCharsVO;
import com.bogdatech.logic.PCUsersService;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pcUsers")
public class PCUsersController {
    @Autowired
    private PCUsersService pcUsersService;

    // 用户初始化
    @PostMapping("/initUser")
    public void initUser(@RequestParam String shopName, @RequestBody PCUsersDO pcUsersDO){
        pcUsersDO.setShopName(shopName);
        pcUsersService.initUser(shopName, pcUsersDO);
    }

    // 添加额度
    @PutMapping("/addPurchasePoints")
    public BaseResponse<Object> addPurchasePoints(@RequestParam String shopName, @RequestBody AddCharsVO addCharsVO){
        return pcUsersService.addPurchasePoints(shopName, addCharsVO.getChars());
    }

    // 查询额度
    @PostMapping("/getPurchasePoints")
    public BaseResponse<Object> getPurchasePoints(@RequestParam String shopName){
        return pcUsersService.getPurchasePoints(shopName);
    }

    // 用户卸载
    @PostMapping("/uninstall")
    public BaseResponse<Object> uninstall(@RequestParam String shopName){
        return pcUsersService.uninstall(shopName);
    }
}
