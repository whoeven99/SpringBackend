package com.bogdatech.logic;

import com.bogdatech.entity.DO.TranslateTextDO;
import com.bogdatech.logic.redis.TranslationParametersRedisService;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.utils.AppInsightsUtils;
import com.bogdatech.utils.CaseSensitiveUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.bogdatech.constants.TranslateConstants.*;
import static com.bogdatech.utils.JsoupUtils.isHtml;
import static com.bogdatech.utils.LiquidHtmlTranslatorUtils.isHtmlEntity;
import static com.bogdatech.utils.RedisKeyUtils.PROGRESS_DONE;
import static com.bogdatech.utils.RedisKeyUtils.generateProcessKey;

@Service
@EnableAsync
public class RabbitMqTranslateService {
    @Autowired
    private ShopifyService shopifyService;
    @Autowired
    private RedisProcessService redisProcessService;
    @Autowired
    private TranslationParametersRedisService translationParametersRedisService;

    public static String AUTO = "auto";

    /**
     * 初始化map集合
     */
    public static Map<String, Set<TranslateTextDO>> initTranslateMap() {
        Map<String, Set<TranslateTextDO>> judgeData = new HashMap<>();
        judgeData.put(HTML, new HashSet<>());
        judgeData.put(PLAIN_TEXT, new HashSet<>());
        judgeData.put(GLOSSARY, new HashSet<>());
        //对于不同key值，存不同的Set里面
        judgeData.put(TITLE, new HashSet<>());
        judgeData.put(META_TITLE, new HashSet<>());
        judgeData.put(LOWERCASE_HANDLE, new HashSet<>());
        //对元字段的LIST_SINGLE_LINE_TEXT_FIELD数据单独处理
        judgeData.put(LIST_SINGLE, new HashSet<>());

        return judgeData;
    }

    /**
     * 翻译停止后，进度条就不加了
     */
    public void checkNeedAddProcessData(String shopName, String target) {
        if (!translationParametersRedisService.isStopped(shopName)) {
            redisProcessService.addProcessData(generateProcessKey(shopName, target), PROGRESS_DONE, 1L);
        }
    }

    public Map<String, Set<TranslateTextDO>> filterTranslateMap(Map<String, Set<TranslateTextDO>> stringSetMap,
                                                                Set<TranslateTextDO> filterTranslateData,
                                                                Map<String, Object> glossaryMap) {
        if (filterTranslateData == null || filterTranslateData.isEmpty()) {
            return stringSetMap;
        }
        for (TranslateTextDO translateTextDO : filterTranslateData) {
            String value = translateTextDO.getSourceText();
            String key = translateTextDO.getTextKey();
            String textType = translateTextDO.getTextType();
            //判断是否是词汇表数据
            if (!glossaryMap.isEmpty()) {
                boolean success = false;
                for (Map.Entry<String, Object> entry : glossaryMap.entrySet()) {
                    String glossaryKey = entry.getKey();
                    if (CaseSensitiveUtils.containsValue(value, glossaryKey) || CaseSensitiveUtils.containsValueIgnoreCase(value, glossaryKey)) {
                        stringSetMap.get(GLOSSARY).add(translateTextDO);
                        success = true;
                        break;
                    }
                }
                if (success) {
                    continue;
                }
            }

            //判断是html还是普通文本
            if (isHtml(value)) {
                stringSetMap.get(HTML).add(translateTextDO);
                continue;
            }
            switch (key) {
                case TITLE -> stringSetMap.get(TITLE).add(translateTextDO);
                case META_TITLE -> stringSetMap.get(META_TITLE).add(translateTextDO);
                case LOWERCASE_HANDLE -> stringSetMap.get(LOWERCASE_HANDLE).add(translateTextDO);
                default -> {
                    if (textType.equals(LIST_SINGLE)) {
                        stringSetMap.get(LIST_SINGLE).add(translateTextDO);
                    } else {
                        stringSetMap.get(PLAIN_TEXT).add(translateTextDO);
                    }
                }
            }
        }
        return stringSetMap;
    }

    // TODO 都改成isCached
    public boolean cacheOrDatabaseTranslateData(String value, String source, Map<String, Object> translation,
                                                String resourceId, ShopifyRequest request) {
        //获取缓存数据
        String targetCache = redisProcessService.getCacheData(request.getTarget(), value);
        if (targetCache != null) {
            targetCache = isHtmlEntity(targetCache);

            // TODO 这里要挪出去
            shopifyService.saveToShopify(targetCache, translation, resourceId, request);
            AppInsightsUtils.printTranslation(targetCache, value, translation, request.getShopName(), "Cache", resourceId, source);

            // 翻译进度条加1
            checkNeedAddProcessData(request.getShopName(), request.getTarget());
            return true;
        }
        return false;
    }
}
