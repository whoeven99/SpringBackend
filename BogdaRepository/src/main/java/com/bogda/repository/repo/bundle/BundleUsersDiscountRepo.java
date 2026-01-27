package com.bogda.repository.repo.bundle;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.repository.entity.BundleUsersDiscountDO;
import com.bogda.repository.mapper.BundleUsersDiscountMapper;
import org.springframework.stereotype.Service;

@Service
public class BundleUsersDiscountRepo extends ServiceImpl<BundleUsersDiscountMapper, BundleUsersDiscountDO> {
    public boolean insertUserDiscount(String shopName, String replaceId, String offerName) {
        return baseMapper.insert(new BundleUsersDiscountDO(shopName, replaceId, offerName, true)) > 0;
    }

    public int getCountByShopName(String shopName) {
        return baseMapper.selectList(new LambdaQueryWrapper<BundleUsersDiscountDO>().eq(BundleUsersDiscountDO::getShopName, shopName)
                .eq(BundleUsersDiscountDO::getStatus, true)).size();
    }
}
