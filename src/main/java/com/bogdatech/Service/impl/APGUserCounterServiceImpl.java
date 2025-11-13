package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IAPGUserCounterService;
import com.bogdatech.entity.DO.APGUserCounterDO;
import com.bogdatech.mapper.APGUserCounterMapper;
import com.bogdatech.utils.CharacterCountUtils;
import org.springframework.stereotype.Service;

import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@Service
public class APGUserCounterServiceImpl extends ServiceImpl<APGUserCounterMapper, APGUserCounterDO> implements IAPGUserCounterService {

    @Override
    public Boolean initUserCounter(String shopName) {
        Long userId = baseMapper.selectUserIdByShopName(shopName);
        //查找数据库中是否有该条数据，如果有，不再插入，如果没有，插入一条数据
        APGUserCounterDO apgUserCounterDO = baseMapper.selectOne(new QueryWrapper<APGUserCounterDO>().eq("user_id", userId));
        if (apgUserCounterDO == null){
            //连表获取User表的id
            APGUserCounterDO userCounterDO = new APGUserCounterDO();
            userCounterDO.setUserId(userId);
            userCounterDO.setId(null);
            return baseMapper.insert(userCounterDO) > 0;
        }
        return true;
    }

    @Override
    public APGUserCounterDO getUserCounter(String shopName) {
        return baseMapper.selectUserCounterByShopName(shopName);
//        return baseMapper.selectOne(new QueryWrapper<APGUserCounterDO>().eq("shop_name", shopName));
    }

    @Override
    public Boolean updateUserUsedCount(Long userId, Integer counter, Integer maxLimit) {
        final int maxRetries = 3;
        final long retryDelayMillis = 500;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            try {

                Boolean b = baseMapper.updateUserUsedCount(userId, counter, maxLimit) > 0;
                if (Boolean.TRUE.equals(b)) {
                    return true;
                } else {
                    retryCount++;
                    appInsights.trackTrace("updateUserUsedCount 更新失败（返回false） errors ，准备第" + retryCount + "次重试，shopName=" + userId);
                }
            } catch (Exception e) {
                retryCount++;
                appInsights.trackTrace("updateUserUsedCount 更新失败（抛异常） errors ，准备第" + retryCount + "次重试，shopName=" + userId + ", 错误=" + e);
            }

            try {
                Thread.sleep(retryDelayMillis);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        appInsights.trackTrace("updateUserUsedCount 更新失败 errors ，重试" + maxRetries + "次后仍未成功，shopName=" + userId);
        return false;

    }

    @Override
    public Boolean updateCharsByUserId(Long id) {
        return baseMapper.updateCharsByUserId(id) > 0;
    }

    @Override
    public Boolean updateUserToken(Long userId, Integer token) {
        return baseMapper.updateUserToken(userId, token) > 0;
    }

    @Override
    public APGUserCounterDO getUserCounterByUserId(Long id) {
        return baseMapper.selectOne(new LambdaQueryWrapper<APGUserCounterDO>().eq(APGUserCounterDO::getUserId, id));
    }

}
