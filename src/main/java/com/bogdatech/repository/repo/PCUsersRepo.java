package com.bogdatech.repository.repo;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.entity.DO.PCUsersDO;
import com.bogdatech.logic.ShopifyService;
import com.bogdatech.logic.redis.OrdersRedisService;
import com.bogdatech.repository.mapper.PCUsersMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;

import static com.bogdatech.constants.TranslateConstants.API_VERSION_LAST;
import static com.bogdatech.requestBody.ShopifyRequestBody.getSingleQuery;
import static com.bogdatech.requestBody.ShopifyRequestBody.getSubscriptionQuery;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.ShopifyUtils.isQueryValid;


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
        appInsights.trackTrace("PC updateCharsByShopName 用户： " + shopName + " orderId: " + orderId + " chars: " + chars + " accessToken: " + accessToken);

        // 根据传来的gid获取， 判断调用那个方法，查询相关订阅信息
        String query;
        if (orderId.contains("AppPurchaseOneTime")) {
            appInsights.trackTrace("一次性购买 用户： " + shopName);
            query = getSingleQuery(orderId);
        } else {
            appInsights.trackTrace("计划购买 用户： " + shopName);
            query = getSubscriptionQuery(orderId);
        }

        String shopifyByQuery = shopifyService.getShopifyData(shopName, accessToken, API_VERSION_LAST, query);
        appInsights.trackTrace("PC addCharsByShopNameAfterSubscribe " + shopName + " 用户 订阅信息 ：" + shopifyByQuery);

        // 判断和解析相关数据
        JSONObject queryValid = isQueryValid(shopifyByQuery);
        if (queryValid == null) {
            appInsights.trackTrace("PC updateCharsByShopName " + shopName + " 用户  errors queryValid : " + queryValid);
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
                .set(PCUsersDO::getUninstallTime, Timestamp.from(Instant.now()))) > 0;
    }

    public boolean updateTrialPurchase(String shopName) {
        return baseMapper.update(new LambdaUpdateWrapper<PCUsersDO>().eq(PCUsersDO::getShopName, shopName)
                .set(PCUsersDO::getUpdateAt, Timestamp.from(Instant.now())).setSql("purchase_points = purchase_points + " + 80000)) > 0;
    }
}
