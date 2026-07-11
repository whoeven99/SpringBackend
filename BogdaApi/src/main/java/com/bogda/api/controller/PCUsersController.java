package com.bogda.api.controller;

import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.entity.DO.PCUsersDO;
import com.bogda.common.entity.VO.AddCharsVO;
import com.bogda.common.entity.VO.TranslationCharsVO;
import org.springframework.web.bind.annotation.*;

import static com.bogda.api.support.DisabledProductEndpoints.error;

@RestController
@RequestMapping("/pcUsers")
public class PCUsersController {

    @PostMapping("/initUser")
    public BaseResponse<Object> initUser(@RequestParam String shopName, @RequestBody PCUsersDO pcUsersDO) {
        return error();
    }

    @PutMapping("/addPurchasePoints")
    public BaseResponse<Object> addPurchasePoints(@RequestParam String shopName, @RequestBody AddCharsVO addCharsVO) {
        return error();
    }

    @PostMapping("/getPurchasePoints")
    public BaseResponse<Object> getPurchasePoints(@RequestParam String shopName) {
        return error();
    }

    @PostMapping("/uninstall")
    public BaseResponse<Object> uninstall(@RequestParam String shopName) {
        return error();
    }

    @PostMapping("/addCharsByShopNameAfterSubscribe")
    public BaseResponse<Object> addCharsByShopNameAfterSubscribe(
            @RequestParam String shopName,
            @RequestBody TranslationCharsVO translationCharsVO) {
        return error();
    }
}
