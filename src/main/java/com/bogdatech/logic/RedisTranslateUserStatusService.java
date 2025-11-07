package com.bogdatech.logic;

import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.entity.DO.TranslatesDO;
import com.bogdatech.integration.RedisIntegration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.bogdatech.utils.RedisKeyUtils.generateTranslateUserStatusKey;

@Component
public class RedisTranslateUserStatusService {
    @Autowired
    private RedisIntegration redisIntegration;
    @Autowired
    private ITranslatesService iTranslatesService;

    /**
     * 存用户每种语言翻译状态
     */
    public void saveTranslateStatus(String shopName, String targetCode, String sourceCode, String status) {
        String key = generateTranslateUserStatusKey(shopName, sourceCode, targetCode);
        redisIntegration.set(key, status);
    }


    /**
     * 获取用户未翻译和部分翻译的语言数
     */
    public List<String> getAll(String shopName, String sourceCode) {
        // 获取该用户所有语言
        List<TranslatesDO> list = iTranslatesService.selectTranslatesByShopNameAndSouce(shopName, sourceCode);

        Set<String> keys = new HashSet<>();
        list.stream().map(TranslatesDO::getTarget).forEach(targetCode -> {
                    String key = generateTranslateUserStatusKey(shopName, sourceCode, targetCode);
                    keys.add(key);
                }
        );

        if (keys.isEmpty()) {
            return Collections.emptyList();
        }

        return redisIntegration.multiGet(keys);
    }
}
