package com.bogda.api.controller;

import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.entity.VO.PCEmailVO;
import com.bogda.repository.entity.PCOrdersDO;
import org.springframework.web.bind.annotation.*;

import static com.bogda.api.support.DisabledProductEndpoints.error;

@RestController
@RequestMapping("/pc/orders")
public class PCOrdersController {

    @PostMapping("/insertOrUpdateOrder")
    public BaseResponse<Object> insertOrUpdateOrder(@RequestParam String shopName, @RequestBody PCOrdersDO pcOrdersDO) {
        return error();
    }

    @PostMapping("/getLatestActiveSubscribeId")
    public BaseResponse<Object> getLatestActiveSubscribeId(@RequestParam String shopName) {
        return error();
    }

    @PostMapping("/sendSubscribeSuccessEmail")
    public BaseResponse<Object> sendSubscribeSuccessEmail(@RequestParam String shopName, @RequestBody PCEmailVO pcEmailVO) {
        return error();
    }

    @PostMapping("/sendOneTimeBuySuccessEmail")
    public BaseResponse<Object> sendOneTimeBuySuccessEmail(@RequestParam String shopName, @RequestBody PCEmailVO pcEmailVO) {
        return error();
    }
}
