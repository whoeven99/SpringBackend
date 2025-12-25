package com.bogda.common.repository.repo;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.common.constants.TranslateConstants;
import com.bogda.common.entity.DO.PCUsersDO;
import com.bogda.common.logic.ShopifyService;
import com.bogda.common.logic.redis.OrdersRedisService;
import com.bogda.common.repository.mapper.PCUsersMapper;
import com.bogda.common.requestBody.ShopifyRequestBody;
import com.bogda.common.utils.CaseSensitiveUtils;
import com.bogda.common.utils.ShopifyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.sql.Timestamp;
import java.time.Instant;


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
        CaseSensitiveUtils.appInsights.trackTrace("PC updateCharsByShopName 用户： " + shopName + " orderId: " + orderId + " chars: " + chars + " accessToken: " + accessToken);

        // 根据传来的gid获取， 判断调用那个方法，查询相关订阅信息
        String query;
        if (orderId.contains("AppPurchaseOneTime")) {
            query = ShopifyRequestBody.getSingleQuery(orderId);
        } else {
            query = ShopifyRequestBody.getSubscriptionQuery(orderId);
        }

        String shopifyByQuery = shopifyService.getShopifyData(shopName, accessToken, TranslateConstants.API_VERSION_LAST, query);
        CaseSensitiveUtils.appInsights.trackTrace("PC addCharsByShopNameAfterSubscribe " + shopName + " 用户 订阅信息 ：" + shopifyByQuery);

        // 判断和解析相关数据
        JSONObject queryValid = ShopifyUtils.isQueryValid(shopifyByQuery);
        if (queryValid == null) {
            CaseSensitiveUtils.appInsights.trackTrace("PC updateCharsByShopName " + shopName + " 用户  errors queryValid : " + queryValid);
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
}
