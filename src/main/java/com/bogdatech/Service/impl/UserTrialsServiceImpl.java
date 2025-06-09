package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IUserTrialsService;
import com.bogdatech.entity.DO.UserTrialsDO;
import com.bogdatech.mapper.UserTrialsMapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;

@Service
public class UserTrialsServiceImpl extends ServiceImpl<UserTrialsMapper, UserTrialsDO> implements IUserTrialsService {

    @Override
    public boolean insertUserTrial(String shopName) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Timestamp end = new Timestamp(now.getTime() + 6 * 60 * 60 * 1000); //暂定5天后过期
        boolean isTrialExpired = false;
        UserTrialsDO userTrialsDO = new UserTrialsDO(null, shopName, now, end, isTrialExpired);
        int insert = baseMapper.insert(userTrialsDO);
        return insert > 0;
    }

    @Override
    public Boolean queryUserTrialByShopName(String shopName) {
        UserTrialsDO userTrialsDO = baseMapper.selectOne(new QueryWrapper<UserTrialsDO>().eq("shop_name", shopName));
        return userTrialsDO.getTrialStart() != null;
    }
}
