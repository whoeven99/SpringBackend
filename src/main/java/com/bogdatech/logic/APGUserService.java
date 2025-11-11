package com.bogdatech.logic;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bogdatech.Service.IAPGUserCounterService;
import com.bogdatech.Service.IAPGUserPlanService;
import com.bogdatech.Service.IAPGUsersService;
import com.bogdatech.entity.DO.APGUsersDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@Service
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
        APGUsersDO apgUsersDO = iapgUsersService.getOne(new QueryWrapper<APGUsersDO>().eq("shop_name", usersDO.getShopName()));
        boolean flag;
        if (apgUsersDO == null) {
            usersDO.setId(null);
            flag = iapgUsersService.save(usersDO);
            APGUsersDO userDO = iapgUsersService.getOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getShopName, usersDO.getShopName()));
            if (userDO == null) {
                return false;
            }

            // 初始化用户counter计数
            iapgUserCounterService.initUserCounter(userDO.getShopName());

            // 插入几条默认官方模板
            apgTemplateService.initializeDefaultTemplate(userDO.getId());

            // 初始化免费计划（20w token额度）
            iapgUserPlanService.initializeFreePlan(userDO.getId());
            tencentEmailService.sendApgInitEmail(userDO.getEmail(), usersDO.getId());
        } else {

            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            usersDO.setLoginTime(now);
            flag = iapgUsersService.update(usersDO, new QueryWrapper<APGUsersDO>().eq("shop_name", usersDO.getShopName()));
        }
        return flag;
    }

    public void uninstallUser(String shopName) {
        // 修改uninstall_time为当前时间
        // 将该用户任务的status改为0
    }
}
