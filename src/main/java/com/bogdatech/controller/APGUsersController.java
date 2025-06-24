package com.bogdatech.controller;

import com.bogdatech.Service.IAPGUsersService;
import com.bogdatech.entity.DO.APGUsersDO;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.utils.RetryUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import static com.bogdatech.utils.RetryUtils.retryWithParam;

@RestController
@RequestMapping("/apg/users")
public class APGUsersController {

    private final IAPGUsersService iapgUsersService;

    @Autowired
    public APGUsersController(IAPGUsersService iapgUsersService) {
        this.iapgUsersService = iapgUsersService;
    }

    /**
     * 存储用户的信息
     * @param shopName 店铺名称
     * @param usersDO 用户信息
     * @return BaseResponse<Object>
     * */
    @PostMapping("/insertOrUpdateApgUser")
    public BaseResponse<Object> insertOrUpdateApgUser(@RequestParam String shopName, @RequestBody APGUsersDO usersDO){
        usersDO.setShopName(shopName);
        boolean result = retryWithParam(
                iapgUsersService::insertOrUpdateApgUser,  // 方法引用，等同于 input -> service.insertOrUpdateApgUser(input)
                usersDO,
                3,        // 最大重试次数
                1000,     // 初始延迟时间：1秒
                8000      // 最大延迟时间：8秒
        );
        if (result){
            return new BaseResponse<>().CreateSuccessResponse(usersDO);
        }
        return new BaseResponse<>().CreateErrorResponse(usersDO);
    }

}
