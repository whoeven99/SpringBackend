package com.bogdatech.logic;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogdatech.Service.IAPGCharsOrderService;
import com.bogdatech.Service.IAPGUserCounterService;
import com.bogdatech.Service.IAPGUserPlanService;
import com.bogdatech.Service.IAPGUsersService;
import com.bogdatech.entity.DO.*;
import org.springframework.stereotype.Service;

import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@Service
public class APGCharsOrderService {
    private final IAPGCharsOrderService charsOrdersService;
    private final IAPGUsersService usersService;
    private final TencentEmailService tencentEmailService;
    private final IAPGUserCounterService iapgUserCounterService;
    private final IAPGUserPlanService iapgUserPlanService;

    public APGCharsOrderService(IAPGCharsOrderService charsOrdersService, IAPGUsersService usersService, TencentEmailService tencentEmailService, IAPGUserCounterService iapgUserCounterService, IAPGUserPlanService iapgUserPlanService) {
        this.charsOrdersService = charsOrdersService;
        this.usersService = usersService;
        this.tencentEmailService = tencentEmailService;
        this.iapgUserCounterService = iapgUserCounterService;
        this.iapgUserPlanService = iapgUserPlanService;
    }

    /**
     * 更新和插入APG_Chars_Order数据
     * */
    public Boolean insertOrUpdateOrder(String shopName, APGCharsOrderDO charsOrdersDO) {
        //获取用户id
        APGUsersDO usersDO = usersService.getOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getShopName, shopName));
        if (usersDO == null) {
            appInsights.trackTrace("APGCharsOrderService 用户 " + shopName + " usersDO is null ");
            return false;
        }
        APGCharsOrderDO charsOrdersServiceById = charsOrdersService.getById(usersDO.getId());
        if (charsOrdersServiceById == null) {
            charsOrdersDO.setUserId(usersDO.getId());
            return charsOrdersService.save(charsOrdersDO);
        }else {
            return charsOrdersService.updateStatusByShopName(usersDO.getId(), charsOrdersDO.getStatus());
        }
    }

    /**
     * 当购买完额度的时候，发送购买邮件
     * */
    public Integer sendAPGPurchaseEmail(String shopName, Integer purchaseToken, Double purchaseAmount) {
        //获取当前用户数据
        APGUsersDO usersDO = usersService.getOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getShopName, shopName));
        //获取当前用户最大额度
        APGUserCounterDO userCounter = iapgUserCounterService.getUserCounter(shopName);
        Integer userMaxLimit = iapgUserPlanService.getUserMaxLimit(usersDO.getId());
        Integer creditBalance = userMaxLimit - userCounter.getUserToken();
        return tencentEmailService.sendAPGPurchaseEmail(usersDO, purchaseToken, purchaseAmount, creditBalance);
    }

}
