package com.bogda.service.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.common.reporter.TraceReporterHolder;
import com.bogda.service.Service.IUserTrialsService;
import com.bogda.common.entity.DO.UserTrialsDO;
import com.bogda.service.mapper.UserTrialsMapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;


@Service
public class UserTrialsServiceImpl extends ServiceImpl<UserTrialsMapper, UserTrialsDO> implements IUserTrialsService {

    @Override
    public boolean insertUserTrial(String shopName) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Timestamp end = new Timestamp(now.getTime() + 5 * 24 * 60 * 60 * 1000); //暂定5天后过期
        boolean isTrialExpired = false;
        UserTrialsDO userTrialsDO = new UserTrialsDO(null, shopName, now, end, isTrialExpired, null);
        int insert = baseMapper.insert(userTrialsDO);
        return insert > 0;
    }

    @Override
    public Boolean queryUserTrialByShopName(String shopName) {
        UserTrialsDO userTrialsDO = baseMapper.selectOne(new QueryWrapper<UserTrialsDO>().eq("shop_name", shopName));
        if (userTrialsDO != null && userTrialsDO.getIsTrialExpired() != null) {
            TraceReporterHolder.report("UserTrialsServiceImpl.queryUserTrialByShopName", "queryUserTrialByShopName " + shopName + " userTrialsDO: " + userTrialsDO);
            return userTrialsDO.getIsTrialExpired();
        }
        return null;
    }

    @Override
    public boolean updateExpiredByShopName(String shopName) {
        UserTrialsDO update = new UserTrialsDO();
        update.setIsTrialExpired(true);
        return baseMapper.update(update, new QueryWrapper<UserTrialsDO>().eq("shop_name", shopName)) > 0;
    }

    @Override
    public UserTrialsDO getDataByShopName(String shopName) {
        return baseMapper.selectOne(new QueryWrapper<UserTrialsDO>().eq("shop_name", shopName));
    }
}
