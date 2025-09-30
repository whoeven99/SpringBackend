package com.bogdatech.logic;

import com.bogdatech.integration.RedisIntegration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import static com.bogdatech.utils.RedisKeyUtils.generateTranslateUserStatusKey;

@Service
public class RedisTranslateUserStatusService {
    @Autowired
    private RedisIntegration redisIntegration;

    /**
     * 存用户每种语言翻译状态
     * */
    public void saveTranslateStatus(String shopName, String targetCode, String sourceCode, String status) {
        String key = generateTranslateUserStatusKey(shopName, sourceCode, targetCode);
        redisIntegration.set(key, status);
    }


    /**
     * 获取用户未翻译和部分翻译的语言数
     * */
    public List<String> getAll(String shopName, String sourceCode) {
        String pattern = String.format("us:%s:%s:*", shopName, sourceCode);
        Set<String> keys = redisIntegration.keys(pattern); // 匹配所有 key
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }

        return redisIntegration.multiGet(keys);
    }
}
