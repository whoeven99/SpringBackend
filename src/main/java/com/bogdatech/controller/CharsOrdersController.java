package com.bogdatech.controller;

import com.bogdatech.entity.CharsOrdersDO;
import com.bogdatech.logic.OrderService;
import com.bogdatech.model.controller.request.PurchaseSuccessRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.bogdatech.enums.ErrorEnum.SQL_INSERT_ERROR;
import static com.bogdatech.enums.ErrorEnum.SQL_SELECT_ERROR;

@RestController
@RequestMapping("/orders")
public class CharsOrdersController {

    private final OrderService orderService;

    @Autowired
    public CharsOrdersController(OrderService orderService) {
        this.orderService = orderService;

    }
    //存储和更新订单
    @PostMapping("/insertOrUpdateOrder")
    public BaseResponse<Object> insertOrUpdateOrder(@RequestBody CharsOrdersDO charsOrdersDO) {
        Boolean b = orderService.insertOrUpdateOrder(charsOrdersDO);
        if (b) {
            return new BaseResponse<>().CreateSuccessResponse(200);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_INSERT_ERROR);
    }

    //查询status为PENDING的
    @GetMapping("/getPendingOrders")
    public BaseResponse<Object> getPendingOrders(String  shopName) {
        List<String> idByShopName = orderService.getIdByShopName(shopName);
        if (idByShopName != null) {
            return new BaseResponse<>().CreateSuccessResponse(idByShopName);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_SELECT_ERROR);
    }

    //购买字符成功后发送相应邮件
    @PostMapping("/sendPurchaseSuccessEmail")
    public BaseResponse<Object> sendPurchaseSuccessEmail(@RequestBody PurchaseSuccessRequest purchaseSuccessRequest) {

        Boolean flag = orderService.sendPurchaseSuccessEmail(purchaseSuccessRequest);
        if (flag) {
            return new BaseResponse<>().CreateSuccessResponse(true);
        }else {
            return new BaseResponse<>().CreateErrorResponse("false");
        }
    }
}
