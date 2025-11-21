package com.bogdatech.repository.repo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.entity.DO.PCUsersDO;
import com.bogdatech.repository.mapper.PCUsersMapper;
import org.springframework.stereotype.Service;
import java.sql.Timestamp;
import java.time.Instant;


@Service
public class PCUsersServiceRepo extends ServiceImpl<PCUsersMapper, PCUsersDO> {
    public PCUsersDO getUserByShopName(String shopName) {
        return baseMapper.selectOne(new LambdaQueryWrapper<PCUsersDO>().eq(PCUsersDO::getShopName, shopName));
    }

    public boolean saveSingleUser(PCUsersDO pcUsersDO) {
        return baseMapper.insert(pcUsersDO) > 0;
    }

    public boolean updateSingleUser(PCUsersDO pcUsersDO) {
        return baseMapper.update(pcUsersDO, new LambdaUpdateWrapper<PCUsersDO>().eq(PCUsersDO::getShopName, pcUsersDO.getShopName())) > 0;
    }

    public boolean updatePurchasePointsByShopName(String shopName, Integer chars) {
        return baseMapper.update(new LambdaUpdateWrapper<PCUsersDO>().eq(PCUsersDO::getShopName, shopName).setSql("purchase_points = purchase_points + " + chars)) > 0;
    }

    public boolean updateUsedPointsByShopName(String shopName, int picFee, Integer limitChars) {
        return baseMapper.update(new LambdaUpdateWrapper<PCUsersDO>().eq(PCUsersDO::getShopName, shopName).setSql("used_points = used_points + " + picFee)) > 0;
    }

    public boolean updateUninstallByShopName(String shopName) {
        Timestamp now = Timestamp.from(Instant.now());
        return baseMapper.update(new LambdaUpdateWrapper<PCUsersDO>().eq(PCUsersDO::getShopName, shopName)
                .set(PCUsersDO::getUninstallTime, now)) > 0;
    }

}
