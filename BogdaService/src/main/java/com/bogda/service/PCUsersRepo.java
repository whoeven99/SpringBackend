package com.bogda.service;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.service.entity.DO.PCUsersDO;
import com.bogda.service.logic.ShopifyService;
import com.bogda.service.logic.redis.OrdersRedisService;
import com.bogda.service.mapper.PCUsersMapper;
import com.bogda.common.contants.TranslateConstants;
import com.bogda.common.utils.AppInsightsUtils;
import com.bogda.common.utils.ShopifyRequestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;

import static com.bogda.service.utils.ShopifyUtils.isQueryValid;


@Service
public class PCUsersRepo extends ServiceImpl<PCUsersMapper, PCUsersDO> {
    @Autowired
    private OrdersRedisService ordersRedisService;
    @Autowired
    private ShopifyService shopifyService;

    public PCUsersDO getUserByShopName(String shopName) {
        return baseMapper.selectOne(new LambdaQueryWrapper<PCUsersDO>().eq(PCUsersDO::getShopName, shopName));
    }

    public boolean saveSingleUser(PCUsersDO pcUsersDO) {
        return baseMapper.insert(pcUsersDO) > 0;
    }

    public boolean updateSingleUser(PCUsersDO pcUsersDO) {
        return baseMapper.update(pcUsersDO, new LambdaUpdateWrapper<PCUsersDO>().eq(PCUsersDO::getShopName, pcUsersDO.getShopName())) > 0;
    }

    public boolean updatePurchasePointsByShopName(String shopName, String accessToken, String orderId, Integer chars) {
        // 添加订单标识
        ordersRedisService.setOrderId(shopName, orderId);

        // 根据gid，判断是否符合添加额度的条件
        AppInsightsUtils.trackTrace("PC updateCharsByShopName 用户： " + shopName + " orderId: " + orderId + " chars: " + chars + " accessToken: " + accessToken);

        // 根据传来的gid获取， 判断调用那个方法，查询相关订阅信息
        String query;
        if (orderId.contains("AppPurchaseOneTime")) {
            query = ShopifyRequestUtils.getSingleQuery(orderId);
        } else {
            query = ShopifyRequestUtils.getSubscriptionQuery(orderId);
        }

        String shopifyByQuery = shopifyService.getShopifyData(shopName, accessToken, TranslateConstants.API_VERSION_LAST, query);
        AppInsightsUtils.trackTrace("PC addCharsByShopNameAfterSubscribe " + shopName + " 用户 订阅信息 ：" + shopifyByQuery);

        // 判断和解析相关数据
        JSONObject queryValid = isQueryValid(shopifyByQuery);
        if (queryValid == null) {
            AppInsightsUtils.trackTrace("PC updateCharsByShopName " + shopName + " 用户  errors queryValid : " + queryValid);
            return false;
        }

        String status = queryValid.getString("status");
        if (!"ACTIVE".equals(status)) {
            return false;
        }

        return baseMapper.update(new LambdaUpdateWrapper<PCUsersDO>().eq(PCUsersDO::getShopName, shopName)
                .set(PCUsersDO::getUpdateAt, Timestamp.from(Instant.now()))
                .setSql("purchase_points = purchase_points + " + chars)) > 0;
    }

    public boolean updateUsedPointsByShopName(String shopName, int picFee) {
        return baseMapper.update(new LambdaUpdateWrapper<PCUsersDO>().eq(PCUsersDO::getShopName, shopName)
                .set(PCUsersDO::getUpdateAt, Timestamp.from(Instant.now()))
                .setSql("used_points = used_points + " + picFee)) > 0;
    }

    public boolean updateUninstallByShopName(String shopName) {
        return baseMapper.update(new LambdaUpdateWrapper<PCUsersDO>().eq(PCUsersDO::getShopName, shopName)
                .set(PCUsersDO::getUninstallTime, Timestamp.from(Instant.now())).set(PCUsersDO::getUpdateAt, Timestamp.from(Instant.now()))) > 0;
    }

    public boolean updateTrialPurchase(String shopName) {
        return baseMapper.update(new LambdaUpdateWrapper<PCUsersDO>().eq(PCUsersDO::getShopName, shopName)
                .set(PCUsersDO::getUpdateAt, Timestamp.from(Instant.now())).setSql("purchase_points = purchase_points + " + 80000)) > 0;
    }

    public boolean updatePurchasePoints(String shopName, int chars) {
        return baseMapper.update(new LambdaUpdateWrapper<PCUsersDO>().eq(PCUsersDO::getShopName, shopName)
                .set(PCUsersDO::getUpdateAt, Timestamp.from(Instant.now())).setSql("purchase_points = purchase_points + " + chars)) > 0;
    }

    public boolean updateUninstallTimeByShopName(String shopName) {
        return baseMapper.update(new LambdaUpdateWrapper<PCUsersDO>().eq(PCUsersDO::getShopName
                , shopName).set(PCUsersDO::getUninstallTime, Timestamp.from(Instant.now()))) > 0;
    }
}
