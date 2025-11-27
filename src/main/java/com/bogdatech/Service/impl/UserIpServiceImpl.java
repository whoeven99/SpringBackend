package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
    public Long getIpCountByShopName(String shopName) {
        return baseMapper.selectOne(new LambdaQueryWrapper<UserIpDO>().eq(UserIpDO::getShopName, shopName)).getTimes();
    }

}
