package com.bogda.api.controller;

import com.bogda.api.logic.PCApp.PCOrdersService;
import com.bogda.api.model.controller.response.BaseResponse;
import com.bogda.api.repository.entity.PCOrdersDO;
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

    // 发送购买计划成功的邮件
    @PostMapping("/sendSubscribeSuccessEmail")
    public BaseResponse<Object> sendSubscribeSuccessEmail(@RequestParam String shopName, @RequestBody com.bogdatech.entity.VO.PCEmailVO pcEmailVO) {
        return pcOrdersService.sendSubscribeSuccessEmail(shopName, pcEmailVO.getSubscribeData());
    }

    // 发送一次性购买成功的邮件
    @PostMapping("/sendOneTimeBuySuccessEmail")
    public BaseResponse<Object> sendOneTimeBuySuccessEmail(@RequestParam String shopName, @RequestBody com.bogdatech.entity.VO.PCEmailVO pcEmailVO) {
        return pcOrdersService.sendOneTimeBuySuccessEmail(shopName, pcEmailVO.getOneTimePurchaseData());
    }

}
