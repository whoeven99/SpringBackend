package com.bogdatech.logic.translate;

import com.bogdatech.entity.DO.TranslateTextDO;
import com.bogdatech.entity.VO.RabbitMqTranslateVO;
import com.bogdatech.logic.*;
import com.bogdatech.logic.redis.TranslationParametersRedisService;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.utils.CharacterCountUtils;
import com.bogdatech.utils.JsoupUtils;
import com.bogdatech.utils.LiquidHtmlTranslatorUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.bogdatech.constants.TranslateConstants.METAFIELD;
import static com.bogdatech.logic.TranslateService.OBJECT_MAPPER;
import static com.bogdatech.logic.redis.TranslationParametersRedisService.generateProgressTranslationKey;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.JsoupUtils.glossaryText;
import static com.bogdatech.utils.JsoupUtils.isHtml;
import static com.bogdatech.utils.LiquidHtmlTranslatorUtils.isHtmlEntity;
import static com.bogdatech.utils.PrintUtils.printTranslation;
import static com.bogdatech.utils.RegularJudgmentUtils.isValidString;
import static com.bogdatech.utils.StringUtils.normalizeHtml;

@Component
public class TranslateDataService {
    @Autowired
    private ShopifyService shopifyService;
    @Autowired
    private JsoupUtils jsoupUtils;
    @Autowired
    private LiquidHtmlTranslatorUtils liquidHtmlTranslatorUtils;
    @Autowired
    private RedisProcessService redisProcessService;
    @Autowired
    private TranslationParametersRedisService translationParametersRedisService;

    public String translateHtmlData(String sourceText, RabbitMqTranslateVO rabbitMqTranslateVO, CharacterCountUtils counter,
                                    ShopifyRequest shopifyRequest, String source,
                                    Map<String, Object> translation, String resourceId) {
        String htmlTranslation;
        try {
            appInsights.trackTrace("定义translateRequest 用户： " + rabbitMqTranslateVO.getShopName() + "，sourceText: " + sourceText);
            TranslateRequest translateRequest = new TranslateRequest(0, rabbitMqTranslateVO.getShopName(), rabbitMqTranslateVO.getAccessToken(), source, rabbitMqTranslateVO.getTarget(), sourceText);

            // 都用分段html翻译
            translationParametersRedisService.hsetTranslationStatus(generateProgressTranslationKey(rabbitMqTranslateVO.getShopName(), rabbitMqTranslateVO.getSource(), rabbitMqTranslateVO.getTarget()), String.valueOf(2));
            translationParametersRedisService.hsetTranslatingString(generateProgressTranslationKey(rabbitMqTranslateVO.getShopName(), rabbitMqTranslateVO.getSource(), rabbitMqTranslateVO.getTarget()), sourceText);

            appInsights.trackTrace("修改进度条的数据 用户： " + rabbitMqTranslateVO.getShopName() + "，sourceText: " + sourceText);
            htmlTranslation = liquidHtmlTranslatorUtils.newJsonTranslateHtml(sourceText, translateRequest, counter, rabbitMqTranslateVO.getLanguagePack(), rabbitMqTranslateVO.getLimitChars(), false);
            appInsights.trackTrace("完成翻译html 用户： " + rabbitMqTranslateVO.getShopName() + "，sourceText: " + sourceText);
            if (rabbitMqTranslateVO.getModeType().equals(METAFIELD)) {
                // 对翻译后的html做格式处理
                appInsights.trackTrace("html所在模块是METAFIELD 用户： " + rabbitMqTranslateVO.getShopName() + "，sourceText: " + sourceText);
                htmlTranslation = normalizeHtml(htmlTranslation);
            }

        } catch (Exception e) {
            appInsights.trackTrace("clickTranslation " + rabbitMqTranslateVO.getShopName() + " html translation errors : " + e.getMessage() + " sourceText: " + sourceText);
            shopifyService.saveToShopify(sourceText, translation, resourceId, shopifyRequest);
            return null;
        }

        appInsights.trackTrace("存到shopify数据到数据库 用户： " + rabbitMqTranslateVO.getShopName() + "，sourceText: " + sourceText);
        return htmlTranslation;
    }

    public String translateListSingleData(String value, String target, RabbitMqTranslateVO rabbitMqTranslateVO,
                                          CharacterCountUtils counter, String shopName, ShopifyRequest shopifyRequest, String source,
                                          Map<String, Object> translation, String resourceId) {
        try {
            // 如果符合要求，则翻译，不符合要求则返回原值
            List<String> resultList = OBJECT_MAPPER.readValue(value, new TypeReference<>() {
            });
            for (int i = 0; i < resultList.size(); i++) {
                String original = resultList.get(i);
                if (!isValidString(original) && original != null && !original.trim().isEmpty() && !isHtml(value)) {
                    // 走翻译流程
                    String targetCache = redisProcessService.getCacheData(target, value);
                    if (targetCache != null) {
                        resultList.set(i, targetCache);
                        continue;
                    }
                    String translated = jsoupUtils.translateByModel(new TranslateRequest(0, shopName, shopifyRequest.getAccessToken(),
                                    source, shopifyRequest.getTarget(), value), counter, rabbitMqTranslateVO.getLanguagePack(),
                            rabbitMqTranslateVO.getLimitChars(), false);
                    translationParametersRedisService.hsetTranslationStatus(generateProgressTranslationKey(shopName,
                            rabbitMqTranslateVO.getSource(), rabbitMqTranslateVO.getTarget()), String.valueOf(2));
                    translationParametersRedisService.hsetTranslatingString(generateProgressTranslationKey(shopName,
                            rabbitMqTranslateVO.getSource(), rabbitMqTranslateVO.getTarget()), value);

                    // 对null的处理
                    if (translated == null) {
                        appInsights.trackTrace("每日须看 translateMetafieldTextData 用户： " + shopName + " 翻译失败，翻译内容为空 value: " + value);
                        translated = jsoupUtils.checkTranslationModel(new TranslateRequest(0, shopName,
                                        shopifyRequest.getAccessToken(), source, shopifyRequest.getTarget(), value), counter,
                                rabbitMqTranslateVO.getLanguagePack(), rabbitMqTranslateVO.getLimitChars(), false);
                        resultList.set(i, translated);
                        continue;
                    }
                    redisProcessService.setCacheData(target, translated, value);
                    //将数据填回去
                    resultList.set(i, translated);
                }
                return OBJECT_MAPPER.writeValueAsString(resultList);
            }
        } catch (Exception e) {
            //存原数据到shopify本地
            shopifyService.saveToShopify(value, translation, resourceId, shopifyRequest);
            appInsights.trackTrace("clickTranslation " + shopName + " LIST errors 错误原因： " + e.getMessage());
        }
        return null;
    }

    public String translateGlossaryData(String value, RabbitMqTranslateVO rabbitMqTranslateVO, CharacterCountUtils counter,
                                        ShopifyRequest shopifyRequest, String source,
                                        Map<String, Object> translation, String resourceId, Integer limitChars,
                                        Map<String, String> keyMap0, Map<String, String> keyMap1) {
        String languagePack = rabbitMqTranslateVO.getLanguagePack();
        String modeType = rabbitMqTranslateVO.getModeType();

        TranslateRequest translateRequest = new TranslateRequest(0, shopifyRequest.getShopName(), shopifyRequest.getAccessToken(), source, shopifyRequest.getTarget(), value);

        String targetText;
        // 判断是否为HTML
        if (isHtml(value)) {
            try {
                targetText = jsoupUtils.translateGlossaryHtml(value, translateRequest, counter, null, keyMap0, keyMap1, languagePack, limitChars, false);
                targetText = isHtmlEntity(targetText);
            } catch (Exception e) {
                shopifyService.saveToShopify(value, translation, resourceId, shopifyRequest);
                return null;
            }

            // TODO：3.2 翻译后的存shopify
            shopifyService.saveToShopify(targetText, translation, resourceId, shopifyRequest);
            printTranslation(targetText, value, translation, shopifyRequest.getShopName(), modeType, resourceId, source);
            return null;
        }

        String finalText = null;

        // 其他数据类型，对数据做处理再翻译
        try {
            // 用大模型翻译
            String glossaryString = glossaryText(keyMap1, keyMap0, value);
            translationParametersRedisService.hsetTranslationStatus(generateProgressTranslationKey(shopifyRequest.getShopName(), source, shopifyRequest.getTarget()), String.valueOf(2));
            translationParametersRedisService.hsetTranslatingString(generateProgressTranslationKey(shopifyRequest.getShopName(), source, shopifyRequest.getTarget()), value);

            // 根据关键词生成对应的提示词
            finalText = jsoupUtils.glossaryTranslationModel(translateRequest, counter, glossaryString, languagePack, limitChars, false);

            // 对null的处理， 不翻译，看下打印情况
            if (finalText == null) {
                appInsights.trackTrace("每日须看 clickTranslation " + shopifyRequest.getShopName() + " glossaryTranslationModel finalText is null " + " sourceText: " + value);
                return null;
            }

        } catch (Exception e) {
            appInsights.trackTrace("clickTranslation " + shopifyRequest.getShopName() + " glossaryTranslationModel errors " + e + " sourceText: " + value);
            shopifyService.saveToShopify(value, translation, resourceId, shopifyRequest);
        }
        return finalText;
    }
}
