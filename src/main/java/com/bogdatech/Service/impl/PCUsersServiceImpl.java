package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IPCUserService;
import com.bogdatech.entity.DO.PCUsersDO;
import com.bogdatech.mapper.PCUsersMapper;
import org.springframework.stereotype.Service;

@Service
public class PCUsersServiceImpl extends ServiceImpl<PCUsersMapper, PCUsersDO> implements IPCUserService {
    @Override
    public PCUsersDO getUserByShopName(String shopName) {
        return baseMapper.selectOne(new LambdaQueryWrapper<PCUsersDO>().eq(PCUsersDO::getShopName, shopName));
    }

    @Override
    public boolean saveSingleUser(PCUsersDO pcUsersDO) {
        return baseMapper.insert(pcUsersDO) > 0;
    }

    @Override
    public boolean updateSingleUser(PCUsersDO pcUsersDO) {
        return baseMapper.update(pcUsersDO, new LambdaUpdateWrapper<PCUsersDO>().eq(PCUsersDO::getShopName, pcUsersDO.getShopName())) > 0;
    }

    @Override
    public boolean updatePurchasePointsByShopName(String shopName, Integer chars) {
        return baseMapper.update(new LambdaUpdateWrapper<PCUsersDO>().eq(PCUsersDO::getShopName, shopName).setSql("purchase_points = purchase_points + " + chars)) > 0;
    }
}
