package com.bogdatech.controller;

import com.bogdatech.entity.DO.APGCharsOrderDO;
import com.bogdatech.logic.APGCharsOrderService;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import static com.bogdatech.enums.ErrorEnum.SQL_INSERT_ERROR;

@RestController
@RequestMapping("/apg/orders")
public class APGCharsOrderController {
    @Autowired
    private  APGCharsOrderService orderService;

    //存储和更新订单
    @PostMapping("/insertOrUpdateOrder")
    public BaseResponse<Object> insertOrUpdateOrder(@RequestParam String shopName, @RequestBody APGCharsOrderDO charsOrdersDO) {
        Boolean b = orderService.insertOrUpdateOrder(shopName, charsOrdersDO);
        if (b) {
            return new BaseResponse<>().CreateSuccessResponse(true);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_INSERT_ERROR);
    }



}
