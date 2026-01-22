package com.bogda.api.controller;

import com.bogda.common.entity.DO.CharsOrdersDO;
import com.bogda.common.entity.VO.TranslationCharsVO;
import com.bogda.service.logic.OrderService;
import com.bogda.common.controller.request.PurchaseSuccessRequest;
import com.bogda.common.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import static com.bogda.common.enums.ErrorEnum.SQL_INSERT_ERROR;

@RestController
@RequestMapping("/orders")
public class CharsOrdersController {
    @Autowired
    private OrderService orderService;

    //存储和更新订单
    @PostMapping("/insertOrUpdateOrder")
    public BaseResponse<Object> insertOrUpdateOrder(@RequestBody CharsOrdersDO charsOrdersDO) {
        Boolean b = orderService.insertOrUpdateOrder(charsOrdersDO);
        if (b) {
            return new BaseResponse<>().CreateSuccessResponse(200);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_INSERT_ERROR);
    }

    //购买字符成功后发送相应邮件
    @PostMapping("/sendPurchaseSuccessEmail")
    public BaseResponse<Object> sendPurchaseSuccessEmail(@RequestBody PurchaseSuccessRequest purchaseSuccessRequest) {

        Boolean flag = orderService.sendPurchaseSuccessEmail(purchaseSuccessRequest);
        if (flag) {
            return new BaseResponse<>().CreateSuccessResponse(true);
        } else {
            return new BaseResponse<>().CreateErrorResponse("false");
        }
    }

    /**
     * 订阅付费计划成功之后发送相关邮件
     */
    @PostMapping("/sendSubscribeSuccessEmail")
    public BaseResponse<Object> sendSubscribeSuccessEmail(@RequestParam String shopName, @RequestBody TranslationCharsVO translationCharsVO) {
        Boolean flag = orderService.sendSubscribeSuccessEmail(shopName, translationCharsVO.getSubGid(), translationCharsVO.getFeeType());
        if (flag) {
            return new BaseResponse<>().CreateSuccessResponse(true);
        } else {
            return new BaseResponse<>().CreateErrorResponse("false");
        }
    }

    /**
     * 查询用户最新一次订阅状态为Active的订阅id
     */
    @PostMapping("/getLatestActiveSubscribeId")
    public BaseResponse<Object> getLatestActiveSubscribeId(@RequestParam String shopName) {
        String latestActiveSubscribeId = orderService.getLatestActiveSubscribeId(shopName);
        if (latestActiveSubscribeId != null) {
            return new BaseResponse<>().CreateSuccessResponse(latestActiveSubscribeId);
        } else {
            return new BaseResponse<>().CreateErrorResponse(false);
        }
    }
}
