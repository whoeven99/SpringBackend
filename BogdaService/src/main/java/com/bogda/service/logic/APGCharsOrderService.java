package com.bogda.service.logic;

import com.bogda.service.Service.IAPGCharsOrderService;
import com.bogda.service.Service.IAPGUserCounterService;
import com.bogda.service.Service.IAPGUserPlanService;
import com.bogda.service.Service.IAPGUsersService;
import com.bogda.service.entity.DO.APGCharsOrderDO;
import com.bogda.service.entity.DO.APGUserCounterDO;
import com.bogda.service.entity.DO.APGUsersDO;
import com.bogda.common.utils.AppInsightsUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class APGCharsOrderService {
    @Autowired
    private IAPGCharsOrderService charsOrdersService;
    @Autowired
    private IAPGUsersService usersService;
    @Autowired
    private TencentEmailService tencentEmailService;
    @Autowired
    private IAPGUserCounterService iapgUserCounterService;
    @Autowired
    private IAPGUserPlanService iapgUserPlanService;


    /**
     * 更新和插入APG_Chars_Order数据
     */
    public Boolean insertOrUpdateOrder(String shopName, APGCharsOrderDO charsOrdersDO) {
        //获取用户id
        APGUsersDO usersDO = usersService.getUserByShopName(shopName);

        if (usersDO == null) {
            AppInsightsUtils.trackTrace("APGCharsOrderService 用户 " + shopName + " usersDO is null ");
            return false;
        }

        APGCharsOrderDO charsOrdersServiceById = charsOrdersService.getById(charsOrdersDO.getId());
        if (charsOrdersServiceById == null) {
            charsOrdersDO.setUserId(usersDO.getId());
            return charsOrdersService.save(charsOrdersDO);
        } else {
            return charsOrdersService.updateStatusByShopName(charsOrdersServiceById.getId(), charsOrdersDO.getStatus());
        }
    }

    /**
     * 当购买完额度的时候，发送购买邮件
     */
    public Integer sendAPGPurchaseEmail(String shopName, Integer purchaseToken, Double purchaseAmount) {
        //获取当前用户数据
        APGUsersDO usersDO = usersService.getUserByShopName(shopName);
        //获取当前用户最大额度
        APGUserCounterDO userCounter = iapgUserCounterService.getUserCounter(shopName);
        Integer userMaxLimit = iapgUserPlanService.getUserMaxLimit(usersDO.getId());
        Integer creditBalance = userMaxLimit - userCounter.getUserToken();
        return tencentEmailService.sendAPGPurchaseEmail(usersDO, purchaseToken, purchaseAmount, creditBalance);
    }

}
