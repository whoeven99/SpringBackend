package com.bogdatech.logic;

import com.bogdatech.Service.IAPGUserCounterService;
import com.bogdatech.Service.IAPGUserPlanService;
import com.bogdatech.Service.IAPGUsersService;
import com.bogdatech.entity.DO.APGUsersDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.sql.Timestamp;
import java.time.Instant;

@Component
public class APGUserService {
    @Autowired
    private IAPGUsersService iapgUsersService;
    @Autowired
    private APGTemplateService apgTemplateService;
    @Autowired
    private IAPGUserPlanService iapgUserPlanService;
    @Autowired
    private TencentEmailService tencentEmailService;
    @Autowired
    private IAPGUserCounterService iapgUserCounterService;

    public Boolean insertOrUpdateApgUser(APGUsersDO usersDO) {
        //先从数据库中获取是否存在对应数据，选择插入或更新
        APGUsersDO apgUsersDO = iapgUsersService.getUserByShopName(usersDO.getShopName());

        boolean flag;
        if (apgUsersDO == null) {
            usersDO.setId(null);
            flag = iapgUsersService.save(usersDO);
            APGUsersDO userDO = iapgUsersService.getUserByShopName(usersDO.getShopName());

            if (userDO == null) {
                return false;
            }

            // 初始化用户counter计数
            iapgUserCounterService.initUserCounter(userDO.getShopName());

            // 插入几条默认官方模板
            apgTemplateService.initializeDefaultTemplate(userDO.getId());

            // 初始化免费计划（20w token额度）
            iapgUserPlanService.initializeFreePlan(userDO.getId());
            tencentEmailService.sendApgInitEmail(userDO.getEmail(), userDO.getId());
        } else {

            Timestamp now = Timestamp.from(Instant.now());
            usersDO.setLoginTime(now);
            flag = iapgUsersService.updateUserByShopName(usersDO, usersDO.getShopName());

        }
        return flag;
    }

    public boolean uninstallUser(String shopName) {
        // 修改uninstall_time为当前时间
        // 将该用户任务的status改为0
        Timestamp now = Timestamp.from(Instant.now());
        return iapgUsersService.uninstallUser(shopName, now);
    }
}
