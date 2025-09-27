package com.bogdatech.logic;

import com.bogdatech.integration.RedisIntegration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import static com.bogdatech.utils.RedisKeyUtils.*;

@Service
public class RedisTranslateLockService {

    @Autowired
    private RedisIntegration redisIntegration;

    /**
     * 对商店加锁
     * 不存在加锁，为1
     * 翻译完后再删掉1
     */
    public boolean lockStore(String shopName) {
        return redisIntegration.trySetValueIfAbsent(generateTranslateLockKey(shopName), TRANSLATE_LOCK_TRUE);
    }


    /**
     * 对商店解锁
     * 将对应的key直接删除
     */
    public boolean unLockStore(String shopName) {
        return redisIntegration.delete(generateTranslateLockKey(shopName));
    }

}
