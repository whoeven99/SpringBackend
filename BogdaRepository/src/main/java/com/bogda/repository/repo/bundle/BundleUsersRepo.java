package com.bogda.repository.repo.bundle;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.repository.entity.BundleUserDO;
import com.bogda.repository.mapper.BundleUsersMapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BundleUsersRepo extends ServiceImpl<BundleUsersMapper, BundleUserDO> {

    /**
     * 获取所有的 shopName（去重）
     */
    public List<String> getAllShopNames() {
        return list().stream()
                .map(BundleUserDO::getShopName)
                .filter(shopName -> shopName != null && !shopName.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    public BundleUserDO getUserByShopName(String shopName) {
        return baseMapper.selectOne(new LambdaQueryWrapper<BundleUserDO>().eq(BundleUserDO::getShopName, shopName));
    }

    public boolean saveUser(BundleUserDO bundleUserDO) {
        return baseMapper.insert(bundleUserDO) > 0;
    }

    public boolean updateUserByShopName(String shopName, BundleUserDO bundleUserDO) {
        return baseMapper.update(bundleUserDO, new LambdaUpdateWrapper<BundleUserDO>()
                .eq(BundleUserDO::getShopName, shopName)) > 0;
    }

    /**
     * 用户卸载：将 uninstall_at 更新为当前 UTC 时间
     * 删除BundleUserDO 里的storefront_access_token 和storefront_id
     */
    public boolean updateUninstallAtByShopName(String shopName) {
        Timestamp now = Timestamp.from(Instant.now());
        return baseMapper.update(new LambdaUpdateWrapper<BundleUserDO>()
                .eq(BundleUserDO::getShopName, shopName)
                .set(BundleUserDO::getUninstallAt, now)
                .set(BundleUserDO::getUpdatedAt, now)
                .set(BundleUserDO::getStorefrontAccessToken, null)
                .set(BundleUserDO::getStorefrontId, null)) > 0;
    }
}
