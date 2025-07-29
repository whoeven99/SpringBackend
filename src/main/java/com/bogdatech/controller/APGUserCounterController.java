package com.bogdatech.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogdatech.Service.IAPGUserCounterService;
import com.bogdatech.Service.IAPGUsersService;
import com.bogdatech.entity.DO.APGUserCounterDO;
import com.bogdatech.entity.DO.APGUsersDO;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import static com.bogdatech.utils.RetryUtils.retryWithParam;

@RestController
@RequestMapping("/apg/userCounter")
public class APGUserCounterController {
    private final IAPGUserCounterService iapgUserCounterService;
    private final IAPGUsersService iapgUsersService;

    @Autowired
    public APGUserCounterController(IAPGUserCounterService iapgUserCounterService, IAPGUsersService iapgUsersService) {
        this.iapgUserCounterService = iapgUserCounterService;
        this.iapgUsersService = iapgUsersService;
    }

    /**
     * 用户计数器初始化
     * */
    @GetMapping("/initUserCounter")
    public BaseResponse<Object> initUserCounter(@RequestParam String shopName){
        boolean result = retryWithParam(
                iapgUserCounterService::initUserCounter,
                shopName,
                3,
                1000,
                8000
        );
        if (result){
            return new BaseResponse<>().CreateSuccessResponse(shopName);
        }
        return new BaseResponse<>().CreateErrorResponse(shopName);
    }

    /**
     * 获取用户计数器信息，4项数据
     * */
    @PostMapping("/getUserCounter")
    public BaseResponse<Object> getUserCounter(@RequestParam String shopName){
        APGUserCounterDO userCounter = iapgUserCounterService.getUserCounter(shopName);
        if (userCounter != null){
            return new BaseResponse<>().CreateSuccessResponse(userCounter);
        }
        return new BaseResponse<>().CreateErrorResponse(shopName);
    }

    /**
     * 更新用户购买的token数
     * */
    @PutMapping("/updateUserToken")
    public BaseResponse<Object> updateUserToken(@RequestParam String shopName, @RequestParam Integer token){
        //获取用户的id
        APGUsersDO usersDO = iapgUsersService.getOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getShopName, shopName));
        Boolean result = iapgUserCounterService.updateUserToken(usersDO.getId(), token);
        if (result) {
            return new BaseResponse<>().CreateSuccessResponse(true);
        }else {
            return new BaseResponse<>().CreateErrorResponse(false);
        }
    }
}
