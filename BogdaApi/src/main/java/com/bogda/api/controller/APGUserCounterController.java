package com.bogda.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogda.service.Service.IAPGUserCounterService;
import com.bogda.service.Service.IAPGUserGeneratedTaskService;
import com.bogda.service.Service.IAPGUserPlanService;
import com.bogda.service.Service.IAPGUsersService;
import com.bogda.service.entity.DO.APGUserCounterDO;
import com.bogda.service.entity.DO.APGUsersDO;
import com.bogda.service.entity.VO.APGTokenVO;
import com.bogda.service.logic.APGCharsOrderService;
import com.bogda.service.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import static com.bogda.common.utils.RetryUtils.retryWithParam;

@RestController
@RequestMapping("/apg/userCounter")
public class APGUserCounterController {
    @Autowired
    private IAPGUserCounterService iapgUserCounterService;
    @Autowired
    private IAPGUsersService iapgUsersService;
    @Autowired
    private IAPGUserPlanService iapgUserPlanService;
    @Autowired
    private APGCharsOrderService apgCharsOrderService;
    @Autowired
    private IAPGUserGeneratedTaskService iapgUserGeneratedTaskService;


    /**
     * 用户计数器初始化
     */
    @GetMapping("/initUserCounter")
    public BaseResponse<Object> initUserCounter(@RequestParam String shopName) {
        boolean result = retryWithParam(
                iapgUserCounterService::initUserCounter,
                shopName,
                3,
                1000,
                8000
        );
        if (result) {
            return new BaseResponse<>().CreateSuccessResponse(shopName);
        }
        return new BaseResponse<>().CreateErrorResponse(shopName);
    }

    /**
     * 获取用户计数器信息，4项数据
     */
    @PostMapping("/getUserCounter")
    public BaseResponse<Object> getUserCounter(@RequestParam String shopName) {
        APGUserCounterDO userCounter = iapgUserCounterService.getUserCounter(shopName);
        //获取总共的额度
        APGUsersDO usersDO = iapgUsersService.getOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getShopName, shopName));
        Integer allToken = iapgUserPlanService.getUserMaxLimit(usersDO.getId());
        APGTokenVO apgTokenVO = new APGTokenVO();
        apgTokenVO.setUserToken(userCounter.getUserToken());
        apgTokenVO.setAllToken(allToken);
        return new BaseResponse<>().CreateSuccessResponse(apgTokenVO);
    }

    /**
     * 更新用户购买的token数
     */
    @PutMapping("/updateUserToken")
    public BaseResponse<Object> updateUserToken(@RequestParam String shopName, @RequestParam Integer token) {
        //获取用户的id
        APGUsersDO usersDO = iapgUsersService.getOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getShopName, shopName));
        Boolean result = iapgUserCounterService.updateUserToken(usersDO.getId(), token);
        //将部分翻译状态改为6
        iapgUserGeneratedTaskService.updateStatusByUserId(usersDO.getId(), 6);
        if (result) {
            return new BaseResponse<>().CreateSuccessResponse(true);
        } else {
            return new BaseResponse<>().CreateErrorResponse(false);
        }
    }

    /**
     * 购买成功的邮件
     */
    @PostMapping("/sendAPGPurchaseEmail")
    public BaseResponse<Object> sendAPGPurchaseEmail(@RequestParam String shopName, @RequestParam Integer token, @RequestParam Double amount) {
        boolean flag = apgCharsOrderService.sendAPGPurchaseEmail(shopName, token, amount) > 0;
        if (flag) {
            return new BaseResponse<>().CreateSuccessResponse(true);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }
}
