package com.bogda.service.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.service.Service.IUserIPSwitchService;
import com.bogda.service.entity.DO.UserIPSwitchDO;
import com.bogda.service.mapper.UserIPSwitchMapper;
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
    public Integer getSwitchId(String shopName) {
        return baseMapper.getSwitchId(shopName);
    }
}
