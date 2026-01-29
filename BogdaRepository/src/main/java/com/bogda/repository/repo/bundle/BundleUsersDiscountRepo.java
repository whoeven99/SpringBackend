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


    public boolean updateDiscountStatus(String shopName, String discountGid, String status) {
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

    public Double getAllGmvByShopName(String shopName) {
        QueryWrapper<BundleUsersDiscountDO> wrapper = new QueryWrapper<>();
        wrapper.eq("shop_name", shopName)
                .eq("is_deleted", 0)
                .select("SUM(gmv) AS total_gmv");

        Map<String, Object> result = baseMapper.selectMaps(wrapper)
                .stream()
                .findFirst()
                .orElse(Collections.emptyMap());

        return result.get("total_gmv") == null
                ? 0D
                : ((Number) result.get("total_gmv")).doubleValue();
    }

    public String getDiscountNameByShopNameAndDiscountId(String shopName, String discountId) {
        List<BundleUsersDiscountDO> bundleUsersDiscountDOS = baseMapper.selectList(new LambdaQueryWrapper<BundleUsersDiscountDO>().eq(BundleUsersDiscountDO::getShopName, shopName)
                .eq(BundleUsersDiscountDO::getDiscountId, discountId));
        if (bundleUsersDiscountDOS.size() > 0) {
            return bundleUsersDiscountDOS.get(0).getDiscountName();
        }

        return null;
    }
}
