package com.bogda.api.controller;

import com.bogda.common.entity.DO.APGCharsOrderDO;
import com.bogda.common.enums.ErrorEnum;
import com.bogda.common.logic.APGCharsOrderService;
import com.bogda.common.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/apg/orders")
public class APGCharsOrderController {
    @Autowired
    private APGCharsOrderService orderService;

    //存储和更新订单
    @PostMapping("/insertOrUpdateOrder")
    public BaseResponse<Object> insertOrUpdateOrder(@RequestParam String shopName, @RequestBody APGCharsOrderDO charsOrdersDO) {
        Boolean b = orderService.insertOrUpdateOrder(shopName, charsOrdersDO);
        if (b) {
            return new BaseResponse<>().CreateSuccessResponse(true);
        }
        return new BaseResponse<>().CreateErrorResponse(ErrorEnum.SQL_INSERT_ERROR);
    }


}
