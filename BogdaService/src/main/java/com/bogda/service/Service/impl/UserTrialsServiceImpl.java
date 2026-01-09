package com.bogda.service.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.service.Service.IUserTrialsService;
import com.bogda.service.entity.DO.UserTrialsDO;
import com.bogda.service.mapper.UserTrialsMapper;
import com.bogda.common.utils.AppInsightsUtils;
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
            AppInsightsUtils.trackTrace("queryUserTrialByShopName " + shopName + " userTrialsDO: " + userTrialsDO);
            return userTrialsDO.getIsTrialExpired();
        }
        return null;
    }

    @Override
    public boolean updateExpiredByShopName(String shopName) {
        return baseMapper.update(new LambdaUpdateWrapper<UserTrialsDO>().eq(UserTrialsDO::getShopName, shopName)
                .set(UserTrialsDO::getIsTrialExpired, true)) > 0;
    }
}
