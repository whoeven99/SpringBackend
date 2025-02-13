package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IUserIPSwitchService;
import com.bogdatech.entity.UserIPSwitchDO;
import com.bogdatech.mapper.UserIPSwitchMapper;
import org.springframework.stereotype.Service;

@Service
public class UserIPSwitchServiceImpl extends ServiceImpl<UserIPSwitchMapper, UserIPSwitchDO> implements IUserIPSwitchService {
    @Override
    public int insertSwitch(UserIPSwitchDO userIPSwitchDO) {
        if (baseMapper.getShopName(userIPSwitchDO.getShopName()) != null) {
            //则更新数据
            return baseMapper.updateSwitchId(userIPSwitchDO.getShopName(), userIPSwitchDO.getSwitchId());
        }
        return baseMapper.insertSwitch(userIPSwitchDO.getShopName(), userIPSwitchDO.getSwitchId());
    }

    @Override
    public int getSwitchId(String shopName) {
        return baseMapper.getSwitchId(shopName);
    }
}
