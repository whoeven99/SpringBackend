package com.bogdatech.logic;

import com.bogdatech.integration.RedisIntegration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.RedisKeyUtils.*;

@Service
public class RedisTranslateLockService {

    @Autowired
    private RedisIntegration redisIntegration;

    private static final int MAX_RETRY = 3;        // 最大重试次数
    private static final long RETRY_INTERVAL = 100; // 重试间隔（毫秒）
    /**
     * 查询商店是否有锁
     * */
    public String isShopLock(String shopName){
        String key = generateTranslateLockKey(shopName);
        System.out.println("key: " + key);
        String getData = redisIntegration.get(key);
        if ("null".equals(getData)){
            getData = null;
        }
        return getData;
    }


    /**
     * 对商店加锁
     * 1： 上锁
     * 0：解锁
     * */
    public boolean lockStore(String shopName, boolean flag){
        String key = generateTranslateLockKey(shopName);
        int retry = 0;
        boolean result = false;

        while (retry < MAX_RETRY) {
            if (flag) {
                // 有锁，修改锁值为1
                result = redisIntegration.tryUpdateIfPresent(key, TRANSLATE_LOCK_TRUE);
            } else {
                // 如果不存在则设置
                result = redisIntegration.trySetValueIfAbsent(key, TRANSLATE_LOCK_TRUE);
            }

            if (result) {
                return true; // 成功就直接返回
            }

            retry++;
            try {
                Thread.sleep(RETRY_INTERVAL * retry);
            } catch (Exception e) {
                Thread.currentThread().interrupt();
                appInsights.trackException(e);
                appInsights.trackTrace("lockStore shopName: " + shopName + " retry: " + retry + " 失败！");
                return false;
            }
        }

        appInsights.trackException(new Exception("lockStore shopName: " + shopName + " retry: " + retry + " 重试后还是失败！"));
        return false; // 重试多次失败
    }


    /**
     * 对商店解锁
     * */
    public boolean unLockStore(String shopName, boolean flag){
        String key = generateTranslateLockKey(shopName);
        int retry = 0;
        boolean result = false;

        while (retry < MAX_RETRY) {
            if (flag) {
                // 有锁，修改锁值为0
                result = redisIntegration.tryUpdateIfPresent(key, TRANSLATE_LOCK_FALSE);
            } else {
                result = redisIntegration.trySetValueIfAbsent(key, TRANSLATE_LOCK_FALSE);
            }

            if (result) {
                return true;
            }

            retry++;
            try {
                Thread.sleep(RETRY_INTERVAL * retry);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                appInsights.trackException(e);
                appInsights.trackTrace("lockStore shopName: " + shopName + " retry: " + retry + " 失败！");
                return false;
            }
        }

        appInsights.trackException(new Exception("lockStore shopName: " + shopName + " retry: " + retry + " 重试后还是失败！"));
        return false;
    }

}
