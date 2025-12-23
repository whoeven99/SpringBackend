package com.bogda.api.logic.token;

import com.bogda.api.Service.impl.TranslationCounterServiceImpl;
import com.bogda.api.entity.DO.TranslationCounterDO;
import com.bogda.api.logic.redis.RedisTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UserTokenService {
    @Autowired
    private TranslationCounterServiceImpl translationCounterService;
    @Autowired
    private RedisTokenRepository redisTokenRepository;

    // Max token以数据库为准
    public Integer getMaxToken(String shopName) {
        return translationCounterService.getMaxCharsByShopName(shopName);
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

    public Integer addUsedToken(String shopName, Integer token) {
        Integer redisUsedToken = redisTokenRepository.getUsedToken(shopName);
        if (redisUsedToken == 0) {
            // 初始化使用
            TranslationCounterDO counterDO = translationCounterService.readCharsByShopName(shopName);
            redisTokenRepository.initUsedToken(shopName, counterDO.getUsedChars());
        }

        redisTokenRepository.addUsedToken(shopName, token);

        Integer usedToken = redisTokenRepository.getUsedToken(shopName);

        // 并发的时候，redis的usedToken是准确的，db只是定时更新而已，不一定准确
        translationCounterService.updateUsedCharsByShopName(shopName, usedToken);
        return usedToken;
    }

    public Integer addUsedToken(String shopName, Integer taskId, Integer token) {
        redisTokenRepository.addUsedToken(shopName, taskId, token);

        Integer usedToken = redisTokenRepository.getUsedToken(shopName);

        // 并发的时候，redis的usedToken是准确的，db只是定时更新而已，不一定准确
        translationCounterService.updateUsedCharsByShopName(shopName, usedToken);
        return usedToken;
    }
}
