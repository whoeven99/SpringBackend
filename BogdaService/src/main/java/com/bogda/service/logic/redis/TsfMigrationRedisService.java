package com.bogda.service.logic.redis;

import com.bogda.repository.RedisIntegration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;

/**
 * 记录已迁移到 TSF 新版翻译的店铺（存本服务自己的 Redis）。
 * 自动翻译任务读取这里，跳过已迁移的店，避免新旧两版重复翻译。
 * TSF 通过 /translate/markShopMigratedToTsf 写入。
 */
@Component
public class TsfMigrationRedisService {
    @Autowired
    private RedisIntegration redisIntegration;

    private static final String TSF_MIGRATED_SHOPS_KEY = "tsf:migrated:shops";

    /** 标记某店已迁移到 TSF（幂等）。 */
    public void markMigrated(String shopName) {
        redisIntegration.setSet(TSF_MIGRATED_SHOPS_KEY, shopName);
    }

    /** 取消标记（预留，便于回退）。 */
    public void unmarkMigrated(String shopName) {
        redisIntegration.remove(TSF_MIGRATED_SHOPS_KEY, shopName);
    }

    /** 已迁移店铺集合；读不到返回空集。 */
    public Set<String> getMigratedShops() {
        Set<String> shops = redisIntegration.getSet(TSF_MIGRATED_SHOPS_KEY);
        return shops != null ? shops : Collections.emptySet();
    }

    public boolean isMigrated(String shopName) {
        return getMigratedShops().contains(shopName);
    }
}
