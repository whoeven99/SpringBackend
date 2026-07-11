package com.bogda.api.controller;

import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.entity.DO.APGCharsOrderDO;
import org.springframework.web.bind.annotation.*;

import static com.bogda.api.support.DisabledProductEndpoints.error;

@RestController
@RequestMapping("/apg/orders")
public class APGCharsOrderController {

    @PostMapping("/insertOrUpdateOrder")
    public BaseResponse<Object> insertOrUpdateOrder(@RequestParam String shopName, @RequestBody APGCharsOrderDO charsOrdersDO) {
        return error();
    }
}
