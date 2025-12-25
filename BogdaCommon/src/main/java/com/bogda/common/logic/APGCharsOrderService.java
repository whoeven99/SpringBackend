package com.bogda.common.logic;


import com.bogda.common.service.IAPGCharsOrderService;
import com.bogda.common.service.IAPGUserCounterService;
import com.bogda.common.service.IAPGUserPlanService;
import com.bogda.common.service.IAPGUsersService;
import com.bogda.common.entity.DO.APGCharsOrderDO;
import com.bogda.common.entity.DO.APGUserCounterDO;
import com.bogda.common.entity.DO.APGUsersDO;
import com.bogda.common.utils.CaseSensitiveUtils;
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
            CaseSensitiveUtils.appInsights.trackTrace("APGCharsOrderService 用户 " + shopName + " usersDO is null ");
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
