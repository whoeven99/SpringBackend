package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IUserTrialsService;
import com.bogdatech.entity.DO.UserTrialsDO;
import com.bogdatech.mapper.UserTrialsMapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;

import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

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
            appInsights.trackTrace("queryUserTrialByShopName " + shopName + " userTrialsDO: " + userTrialsDO);
            return userTrialsDO.getIsTrialExpired();
        }
        return null;
    }

    @Override
    public UserTrialsDO getUserTrialByShopName(String shopName) {
        return this.getOne(new LambdaQueryWrapper<UserTrialsDO>().eq(UserTrialsDO::getShopName, shopName));
    }

    @Override
    public boolean updateTrialsExpiredByShopName(String shopName, boolean trialsExpired) {
        return baseMapper.update(new LambdaUpdateWrapper<UserTrialsDO>().eq(UserTrialsDO::getShopName, shopName)
                .set(UserTrialsDO::getIsTrialExpired, trialsExpired)) > 0;
    }

    @Override
    public boolean updateTrialShowByShopName(String shopName, boolean b) {
        return baseMapper.update(new LambdaUpdateWrapper<UserTrialsDO>().eq(UserTrialsDO::getShopName, shopName)
                .set(UserTrialsDO::getIsTrialShow, b)) > 0;
    }

    @Override
    public List<UserTrialsDO> selectTrialsByIsTrialExpired(boolean trialExpired) {
        return  baseMapper.selectList(new LambdaQueryWrapper<UserTrialsDO>().eq(UserTrialsDO::getIsTrialShow, false));
    }
}
