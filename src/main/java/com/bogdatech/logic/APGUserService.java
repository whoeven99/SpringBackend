package com.bogdatech.logic;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bogdatech.Service.IAPGUsersService;
import com.bogdatech.entity.DO.APGUsersDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@Service
public class APGUserService {
    private final IAPGUsersService iapgUsersService;
    private final APGTemplateService apgTemplateService;

    @Autowired
    public APGUserService(IAPGUsersService iapgUsersService, APGTemplateService apgTemplateService) {
        this.iapgUsersService = iapgUsersService;
        this.apgTemplateService = apgTemplateService;
    }

    public Boolean insertOrUpdateApgUser(APGUsersDO usersDO) {
        //先从数据库中获取是否存在对应数据，选择插入或更新
        APGUsersDO shopName = iapgUsersService.getOne(new QueryWrapper<APGUsersDO>().eq("shop_name", usersDO.getShopName()));
        boolean flag;
        if (shopName == null) {
            flag = iapgUsersService.save(usersDO);
            //插入几条默认官方模板
            apgTemplateService.InitializeDefaultTemplate(usersDO.getShopName());
        }else {
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            usersDO.setLoginTime(now);
            flag = iapgUsersService.update(usersDO,new QueryWrapper<APGUsersDO>().eq("shop_name",usersDO.getShopName()));
        }
        return flag;
    }
}
