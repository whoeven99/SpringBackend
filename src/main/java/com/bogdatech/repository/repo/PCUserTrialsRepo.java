package com.bogdatech.repository.repo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.repository.entity.PCUserTrialsDO;
import com.bogdatech.repository.mapper.PCUserTrialsMapper;
import org.springframework.stereotype.Service;
import java.sql.Timestamp;
import java.time.Instant;

import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@Service
public class PCUserTrialsRepo extends ServiceImpl<PCUserTrialsMapper, PCUserTrialsDO> {
    public PCUserTrialsDO getUserTrialByShopName(String shopName) {
        return baseMapper.selectOne(new LambdaQueryWrapper<PCUserTrialsDO>().eq(PCUserTrialsDO::getShopName, shopName));
    }

    public Boolean insertUserTrial(String shopName) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Timestamp end = new Timestamp(now.getTime() + 5 * 24 * 60 * 60 * 1000); //暂定5天后过期
        boolean isTrialExpired = false;
        PCUserTrialsDO pcUserTrialsDO = new PCUserTrialsDO(shopName, now, end, isTrialExpired, null, null);
        int insert = baseMapper.insert(pcUserTrialsDO);
        return insert > 0;
    }

    public Boolean queryUserTrialByShopName(String shopName) {
        PCUserTrialsDO userTrialsDO = baseMapper.selectOne(new LambdaQueryWrapper<PCUserTrialsDO>().eq(PCUserTrialsDO::getShopName, shopName));
        if (userTrialsDO != null && userTrialsDO.getIsTrialExpired() != null) {
            appInsights.trackTrace("queryUserTrialByShopName " + shopName + " userTrialsDO: " + userTrialsDO);
            return userTrialsDO.getIsTrialExpired();
        }
        return null;
    }

    public boolean updateTrialShowByShopName(String shopName) {
        return baseMapper.update(new LambdaUpdateWrapper<PCUserTrialsDO>().eq(PCUserTrialsDO::getShopName, shopName)
                .set(PCUserTrialsDO::getIsTrialShow, true).set(PCUserTrialsDO::getUpdatedAt, Timestamp.from(Instant.now()))) > 0;
    }

    public boolean insertUserTrialAndBeginAndEnd(String shopName, Timestamp beginTimestamp, Timestamp afterTrialDaysTimestamp) {
        PCUserTrialsDO pcUserTrialsDO = new PCUserTrialsDO();
        pcUserTrialsDO.setShopName(shopName);
        pcUserTrialsDO.setTrialStart(beginTimestamp);
        pcUserTrialsDO.setTrialEnd(afterTrialDaysTimestamp);
        return baseMapper.insert(pcUserTrialsDO) > 0;
    }

    public boolean updateTrialExpiredByShopName(String shopName) {
        return baseMapper.update(new LambdaUpdateWrapper<PCUserTrialsDO>().eq(PCUserTrialsDO::getShopName, shopName)
                .set(PCUserTrialsDO::getIsTrialExpired, true)) > 0;
    }
}
