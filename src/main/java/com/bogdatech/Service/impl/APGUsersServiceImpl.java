package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IAPGUsersService;
import com.bogdatech.entity.DO.APGUsersDO;
import com.bogdatech.mapper.APGUsersMapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;

@Service
public class APGUsersServiceImpl extends ServiceImpl<APGUsersMapper, APGUsersDO> implements IAPGUsersService {
    @Override
    public APGUsersDO getUserByShopName(String shopName) {
        return baseMapper.selectOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getShopName, shopName));
    }

    @Override
    public boolean uninstallUser(String shopName, Timestamp now) {
        return baseMapper.update(new LambdaUpdateWrapper<APGUsersDO>().eq(APGUsersDO::getShopName, shopName)
                .set(APGUsersDO::getUninstallTime, now)) > 0;
    }
}
