package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IAPGUsersService;
import com.bogdatech.entity.DO.APGUsersDO;
import com.bogdatech.mapper.APGUsersMapper;
import org.springframework.stereotype.Service;

@Service
public class APGUsersServiceImpl extends ServiceImpl<APGUsersMapper, APGUsersDO> implements IAPGUsersService {
    @Override
    public APGUsersDO getUserByShopName(String shopName) {
        return this.getOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getShopName, shopName));
    }

    @Override
    public boolean updateUserByShopName(APGUsersDO usersDO, String shopName) {
        return  baseMapper.update(usersDO, new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getShopName, usersDO.getShopName())) > 0;
    }

    @Override
    public APGUsersDO getUserByUserId(Long subtaskId) {
        return baseMapper.selectOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getId, subtaskId));
    }
}
