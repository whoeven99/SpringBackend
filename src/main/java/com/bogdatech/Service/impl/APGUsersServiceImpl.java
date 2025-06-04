package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IAPGUsersService;
import com.bogdatech.entity.DO.APGUsersDO;
import com.bogdatech.mapper.APGUsersMapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@Service
public class APGUsersServiceImpl extends ServiceImpl<APGUsersMapper, APGUsersDO> implements IAPGUsersService {
    @Override
    public Boolean insertOrUpdateApgUser(APGUsersDO usersDO) {
        //先从数据库中获取是否存在对应数据，选择插入或更新
        APGUsersDO shopName = baseMapper.selectOne(new QueryWrapper<APGUsersDO>().eq("shop_name", usersDO.getShopName()));
        int flag;
        if (shopName == null) {
            flag = baseMapper.insert(usersDO);
        }else {
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            usersDO.setLoginTime(now);
            flag = baseMapper.update(usersDO,new QueryWrapper<APGUsersDO>().eq("shop_name",usersDO.getShopName()));
        }
        return flag > 0;
    }
}
