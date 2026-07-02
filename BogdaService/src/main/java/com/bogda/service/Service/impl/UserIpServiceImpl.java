package com.bogda.service.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.service.Service.IUserIpService;
import com.bogda.common.entity.DO.UserIpDO;
import com.bogda.service.mapper.UserIpMapper;
import org.springframework.stereotype.Service;

@Service
public class UserIpServiceImpl extends ServiceImpl<UserIpMapper, UserIpDO> implements IUserIpService {
    @Override
    public Boolean addOrUpdateUserIp(String shopName) {
        UserIpDO userIpDO = baseMapper.selectOne(
                new LambdaQueryWrapper<UserIpDO>().eq(UserIpDO::getShopName, shopName));
        if (userIpDO == null) {
            UserIpDO newUserIpDO = new UserIpDO();
            newUserIpDO.setShopName(shopName);
            return baseMapper.insert(newUserIpDO) > 0;
        }
        return true;
    }
}
