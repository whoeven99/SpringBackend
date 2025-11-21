package com.bogdatech.controller;

import com.bogdatech.logic.PCApp.PCOrdersService;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.repository.entity.PCOrdersDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/pc/orders")
public class PCOrdersController {
    @Autowired
    private PCOrdersService pcOrdersService;

    // 存储和更新订单
    @PostMapping("/insertOrUpdateOrder")
    public BaseResponse<Object> insertOrUpdateOrder(@RequestParam String shopName, @RequestBody PCOrdersDO pcOrdersDO) {
        pcOrdersDO.setShopName(shopName);
        return pcOrdersService.insertOrUpdateOrder(pcOrdersDO);
    }

    /**
     * 查询用户最新一次订阅状态为Active的订阅id
     * */
    @PostMapping("/getLatestActiveSubscribeId")
    public BaseResponse<Object> getLatestActiveSubscribeId(@RequestParam String shopName) {
        return pcOrdersService.getLatestActiveSubscribeId(shopName);
    }
}
