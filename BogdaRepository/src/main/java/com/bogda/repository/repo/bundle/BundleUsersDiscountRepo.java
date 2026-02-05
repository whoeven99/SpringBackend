package com.bogda.repository.repo.bundle;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.repository.entity.BundleUsersDiscountDO;
import com.bogda.repository.mapper.BundleUsersDiscountMapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class BundleUsersDiscountRepo extends ServiceImpl<BundleUsersDiscountMapper, BundleUsersDiscountDO> {
    public boolean insertUserDiscount(String shopName, String replaceId, String offerName) {
        BundleUsersDiscountDO bundleUsersDiscountDO = new BundleUsersDiscountDO();
        bundleUsersDiscountDO.setShopName(shopName);
        bundleUsersDiscountDO.setDiscountId(replaceId);
        bundleUsersDiscountDO.setDiscountName(offerName);
        bundleUsersDiscountDO.setStatus(true);
        return baseMapper.insert(bundleUsersDiscountDO) > 0;
    }


    public boolean updateDiscountStatus(String shopName, String discountGid, Boolean status) {
        return baseMapper.update(new LambdaUpdateWrapper<BundleUsersDiscountDO>()
                .eq(BundleUsersDiscountDO::getShopName, shopName)
                .eq(BundleUsersDiscountDO::getDiscountId, discountGid)
                .set(BundleUsersDiscountDO::getStatus, status)) > 0;
    }

    public boolean updateDiscountDelete(String shopName, String discountGid, boolean isDeleted) {
        return baseMapper.update(new LambdaUpdateWrapper<BundleUsersDiscountDO>()
                .eq(BundleUsersDiscountDO::getShopName, shopName)
                .eq(BundleUsersDiscountDO::getDiscountId, discountGid)
                .set(BundleUsersDiscountDO::getIsDeleted, isDeleted)) > 0;
    }

    public List<BundleUsersDiscountDO> getAllByShopName(String shopName) {
        return baseMapper.selectList(new LambdaQueryWrapper<BundleUsersDiscountDO>()
                .eq(BundleUsersDiscountDO::getShopName, shopName));
    }

    /**
     * 查询所有状态为活跃且未删除的折扣条目（供 UTC 0 点日预算重置任务使用）
     */
    public List<BundleUsersDiscountDO> listActiveAndNotDeleted() {
        return baseMapper.selectList(new LambdaQueryWrapper<BundleUsersDiscountDO>()
                .eq(BundleUsersDiscountDO::getStatus, true)
                .eq(BundleUsersDiscountDO::getIsDeleted, false));
    }

    public String getDiscountNameByShopNameAndDiscountId(String shopName, String discountId) {
        List<BundleUsersDiscountDO> bundleUsersDiscountDOS = baseMapper.selectList(new LambdaQueryWrapper<BundleUsersDiscountDO>().eq(BundleUsersDiscountDO::getShopName, shopName)
                .eq(BundleUsersDiscountDO::getDiscountId, discountId));
        if (bundleUsersDiscountDOS.size() > 0) {
            return bundleUsersDiscountDOS.get(0).getDiscountName();
        }

        return null;
    }

    /**
     * 累积更新折扣数据：将前一天的 exposure_pv、add_to_cart_pv、checkout_started_pv、gmv 累加到数据库
     */
    public boolean updateAccumulateDiscountData(String shopName, String discountId,
                                                int exposurePv, int addToCartPv, int checkoutStartedPv, double gmv) {
        String sql = "exposure_pv = exposure_pv + " + exposurePv +
                ", add_to_cart_pv = add_to_cart_pv + " + addToCartPv +
                ", checkout_started_pv = checkout_started_pv + " + checkoutStartedPv +
                ", gmv = gmv + " + gmv;
        return baseMapper.update(null, new LambdaUpdateWrapper<BundleUsersDiscountDO>()
                .eq(BundleUsersDiscountDO::getShopName, shopName)
                .eq(BundleUsersDiscountDO::getDiscountId, discountId)
                .setSql(sql)) > 0;
    }

    public BundleUsersDiscountDO getAllByShopNameAndDiscountName(String shopName, String discountName) {
        return baseMapper.selectOne(new LambdaQueryWrapper<BundleUsersDiscountDO>()
                .eq(BundleUsersDiscountDO::getShopName, shopName)
                .eq(BundleUsersDiscountDO::getDiscountName, discountName));
    }
}
