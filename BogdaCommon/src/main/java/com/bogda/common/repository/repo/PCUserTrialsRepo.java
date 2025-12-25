package com.bogda.common.repository.repo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.common.repository.entity.PCUserTrialsDO;
import com.bogda.common.repository.mapper.PCUserTrialsMapper;
import com.bogda.common.utils.CaseSensitiveUtils;
import org.springframework.stereotype.Service;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
public class PCUserTrialsRepo extends ServiceImpl<PCUserTrialsMapper, PCUserTrialsDO> {
    public PCUserTrialsDO getUserTrialByShopName(String shopName) {
        return baseMapper.selectOne(new LambdaQueryWrapper<PCUserTrialsDO>().eq(PCUserTrialsDO::getShopName, shopName));
    }

    public Boolean insertUserTrial(String shopName) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Timestamp end = new Timestamp(now.getTime() + 5 * 24 * 60 * 60 * 1000); //暂定5天后过期
        boolean isTrialExpired = true;
        PCUserTrialsDO pcUserTrialsDO = new PCUserTrialsDO(shopName, now, end, isTrialExpired, null, null);
        int insert = baseMapper.insert(pcUserTrialsDO);
        return insert > 0;
    }

    public Boolean queryUserTrialByShopName(String shopName) {
        PCUserTrialsDO userTrialsDO = baseMapper.selectOne(new LambdaQueryWrapper<PCUserTrialsDO>().eq(PCUserTrialsDO::getShopName, shopName));
        if (userTrialsDO != null && userTrialsDO.getIsTrialExpired() != null) {
            CaseSensitiveUtils.appInsights.trackTrace("PC queryUserTrialByShopName " + shopName + " userTrialsDO : " + userTrialsDO);
            return userTrialsDO.getIsTrialExpired();
        }
        return false;
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

    public boolean updateTrialExpiredByShopName(String shopName, boolean isTrialExpired) {
        return baseMapper.update(new LambdaUpdateWrapper<PCUserTrialsDO>().eq(PCUserTrialsDO::getShopName, shopName)
                .set(PCUserTrialsDO::getIsTrialExpired, isTrialExpired)) > 0;
    }

    public List<PCUserTrialsDO> getNotExpiredTrialByShopName() {
        return baseMapper.selectList(new LambdaQueryWrapper<PCUserTrialsDO>().eq(PCUserTrialsDO::getIsTrialExpired, false));
    }
}
