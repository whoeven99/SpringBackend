package com.bogda.service.logic.token;

import com.bogda.common.entity.VO.TokenQuotaVO;
import com.bogda.service.Service.impl.TranslationCounterServiceImpl;
import com.bogda.common.entity.DO.TranslationCounterDO;
import com.bogda.service.logic.redis.RedisTokenRepository;
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

    /**
     * 外部扣减额度接口：先扣Redis，再同步到DB，返回最新剩余额度。
     * remaining 可能为负数（允许超额消费）。
     *
     * @param shopName 店铺名
     * @param tokens   本次消耗的token数
     * @return TokenQuotaVO 包含 maxToken / usedToken / remaining
     */
    public TokenQuotaVO deductAndGetRemaining(String shopName, Integer tokens) {
        // 1. 获取DB中的最大额度
        Integer maxToken = translationCounterService.getMaxCharsByShopName(shopName);

        // 2. 先扣Redis（Redis是实时的真相来源）
        Integer redisUsedToken = redisTokenRepository.getUsedToken(shopName);
        if (redisUsedToken == 0) {
            TranslationCounterDO counterDO = translationCounterService.readCharsByShopName(shopName);
            if (counterDO != null) {
                redisTokenRepository.initUsedToken(shopName, counterDO.getUsedChars());
            }
        }
        redisTokenRepository.addUsedToken(shopName, tokens);
        Integer newUsedToken = redisTokenRepository.getUsedToken(shopName);

        // 3. 同步Redis值到DB（异步镜像，尽力而为）
        translationCounterService.updateUsedCharsByShopName(shopName, newUsedToken);

        // 4. 计算剩余额度（可能为负数）
        int remaining = (maxToken != null ? maxToken : 0) - newUsedToken;

        return new TokenQuotaVO(shopName, maxToken, newUsedToken, remaining);
    }
}
