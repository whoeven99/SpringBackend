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

    public Integer addUsedToken(String shopName, Integer token) {
        Integer redisUsedToken = redisTokenRepository.getUsedToken(shopName);
        if (redisUsedToken == 0) {
            TranslationCounterDO counterDO = translationCounterService.readCharsByShopName(shopName);
            redisTokenRepository.initUsedToken(shopName, counterDO.getUsedChars());
        }

        redisTokenRepository.addUsedToken(shopName, token);

        Integer usedToken = redisTokenRepository.getUsedToken(shopName);

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
        Integer maxToken = translationCounterService.getMaxCharsByShopName(shopName);

        Integer redisUsedToken = redisTokenRepository.getUsedToken(shopName);
        if (redisUsedToken == 0) {
            TranslationCounterDO counterDO = translationCounterService.readCharsByShopName(shopName);
            if (counterDO != null) {
                redisTokenRepository.initUsedToken(shopName, counterDO.getUsedChars());
            }
        }
        redisTokenRepository.addUsedToken(shopName, tokens);
        Integer newUsedToken = redisTokenRepository.getUsedToken(shopName);

        translationCounterService.updateUsedCharsByShopName(shopName, newUsedToken);

        int remaining = (maxToken != null ? maxToken : 0) - newUsedToken;

        return new TokenQuotaVO(shopName, maxToken, newUsedToken, remaining);
    }
}
