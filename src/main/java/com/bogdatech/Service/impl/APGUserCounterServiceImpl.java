package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IAPGUserCounterService;
import com.bogdatech.entity.DO.APGUserCounterDO;
import com.bogdatech.mapper.APGUserCounterMapper;
import org.springframework.stereotype.Service;

@Service
public class APGUserCounterServiceImpl extends ServiceImpl<APGUserCounterMapper, APGUserCounterDO> implements IAPGUserCounterService {
    @Override
    public Boolean initUserCounter(String shopName) {
        //查找数据库中是否有该条数据，如果有，不再插入，如果没有，插入一条数据
        APGUserCounterDO apgUserCounterDO = baseMapper.selectOne(new QueryWrapper<APGUserCounterDO>().eq("shop_name", shopName));
        if (apgUserCounterDO == null){
            APGUserCounterDO userCounterDO = new APGUserCounterDO();
            userCounterDO.setShopName(shopName);
            userCounterDO.setId(null);
            return baseMapper.insert(userCounterDO) > 0;
        }
        return true;
    }

    @Override
    public APGUserCounterDO getUserCounter(String shopName) {
        return baseMapper.selectOne(new QueryWrapper<APGUserCounterDO>().eq("shop_name", shopName));
    }
}
