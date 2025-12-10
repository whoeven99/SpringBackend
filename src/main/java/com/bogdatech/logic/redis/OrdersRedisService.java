package com.bogdatech.logic.redis;

import com.bogdatech.integration.RedisIntegration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OrdersRedisService {
    @Autowired
    private RedisIntegration redisIntegration;

    // 存一个订单的id 做标识
    private static String ORDER_ID = "oi:{shopName}:{orderId}";

    public static String generalOrderIdKey(String shopName, String orderId) {
        if (shopName == null ) {
            return null;
        }

        return ORDER_ID.replace("{shopName}", shopName)
                .replace("{orderId}", orderId);
    }

    public void setOrderId(String shopName, String orderId) {
        redisIntegration.set(generalOrderIdKey(shopName, orderId), orderId);
        redisIntegration.expire(generalOrderIdKey(shopName, orderId), 43200L);
    }

    public String getOrderId(String shopName, String orderId) {
        return redisIntegration.get(generalOrderIdKey(shopName, orderId));
    }

}
