package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IUserIpService;
import com.bogdatech.entity.DO.UserIpDO;
import com.bogdatech.mapper.UserIpMapper;
import org.springframework.stereotype.Service;

@Service
public class UserIpServiceImpl extends ServiceImpl<UserIpMapper, UserIpDO> implements IUserIpService {
    @Override
    public Boolean addOrUpdateUserIp(String shopName) {
        //查是数据库中是否存在，存在返回true，不存在插入
        UserIpDO userIpDO = baseMapper.selectOne(new QueryWrapper<UserIpDO>().eq("shop_name", shopName));
        if (userIpDO == null) {
            UserIpDO newUserIpDO = new UserIpDO();
            newUserIpDO.setShopName(shopName);
            //初始化UserIp表
            return baseMapper.insert(newUserIpDO) > 0;
        }
        return true;
    }

    @Override
    public UserIpDO selectByShopNameForUpdate(String shopName) {
        return baseMapper.selectByShopNameForUpdate(shopName);
    }

    @Override
    public Boolean updateIpByFirstEmailAndShopName(UserIpDO userIpDO, String shopName, boolean flag) {
        return baseMapper.update(userIpDO, new LambdaUpdateWrapper<UserIpDO>()
                .eq(UserIpDO::getShopName, shopName).eq(UserIpDO::getFirstEmail, flag)) > 0;
    }

    @Override
    public Boolean updateIpBySecondEmailAndShopName(UserIpDO userIpDO, String shopName, boolean b) {
        return baseMapper.update(userIpDO, new LambdaUpdateWrapper<UserIpDO>().eq(UserIpDO::getShopName, shopName)
                .eq(UserIpDO::getSecondEmail, false)) > 0;
    }

    @Override
    public boolean resetUsersFreeIp(String shopName) {
        return baseMapper.update(new LambdaUpdateWrapper<UserIpDO>().eq(UserIpDO::getShopName, shopName)
                .set(UserIpDO::getTimes, 0).set(UserIpDO::getFirstEmail, 0).set(UserIpDO::getSecondEmail, 0)) > 0;
    }

}
