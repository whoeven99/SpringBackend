package com.bogdatech.logic.token;

import com.bogdatech.Service.impl.SubscriptionPlansServiceImpl;
import com.bogdatech.Service.impl.TranslationCounterServiceImpl;
import com.bogdatech.entity.DO.TranslationCounterDO;
import com.bogdatech.logic.redis.RedisTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UserTokenService {
    @Autowired
    private TranslationCounterServiceImpl translationCounterService;
    @Autowired
    private SubscriptionPlansServiceImpl subscriptionPlansService;
    @Autowired
    private RedisTokenRepository redisTokenRepository;

    // Max token以数据库为准
    public Integer getMaxToken(String shopName) {
        Integer planChars = subscriptionPlansService.getCharsByPlanName(shopName);
        TranslationCounterDO counterDO = translationCounterService.readCharsByShopName(shopName);
        return counterDO.getTotalChars() + planChars;
    }

    // Used token以Redis为准
    public Integer getUsedToken(String shopName) {
        Integer usedToken = redisTokenRepository.getUsedToken(shopName);
        if (usedToken == 0) {
            // 初始化使用
            TranslationCounterDO counterDO = translationCounterService.readCharsByShopName(shopName);
            redisTokenRepository.initUsedToken(shopName, counterDO.getUsedChars());
            return counterDO.getUsedChars();
        }
        return usedToken;
    }

    public Integer getUsedTokenByTaskId(String shopName, Integer taskId) {
        return redisTokenRepository.getUsedTokenByTaskId(shopName, taskId);
    }

    public Integer addUsedToken(String shopName, Integer taskId, Integer token) {
        redisTokenRepository.addUsedToken(shopName, taskId, token);

        Integer usedToken = redisTokenRepository.getUsedToken(shopName);

        // 并发的时候，redis的usedToken是准确的，db只是定时更新而已，不一定准确
        translationCounterService.updateUsedCharsByShopName(shopName, usedToken);
        return usedToken;
    }
}
