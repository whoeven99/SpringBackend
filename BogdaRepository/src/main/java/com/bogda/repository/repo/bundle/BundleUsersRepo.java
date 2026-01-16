package com.bogda.repository.repo.bundle;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.repository.entity.BundleUserDO;
import com.bogda.repository.mapper.BundleUsersMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class BundleUsersRepo extends ServiceImpl<BundleUsersMapper, BundleUserDO> {

    public BundleUserDO getUserByShopName(String shopName) {
        return baseMapper.selectOne(new LambdaQueryWrapper<BundleUserDO>().eq(BundleUserDO::getShopName, shopName));
    }

    public boolean saveUser(BundleUserDO bundleUserDO) {
        String appEnv = System.getenv("ApplicationEnv");
        if ("dev".equals(appEnv)) {

        }
        return baseMapper.insert(bundleUserDO) > 0;
    }

    public boolean updateUserByShopName(String shopName, BundleUserDO bundleUserDO) {
        return baseMapper.update(bundleUserDO, new LambdaUpdateWrapper<BundleUserDO>().eq(BundleUserDO::getShopName, shopName)) > 0;
    }
}
