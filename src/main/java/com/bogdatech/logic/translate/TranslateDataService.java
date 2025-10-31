package com.bogdatech.logic.translate;

import com.bogdatech.entity.VO.RabbitMqTranslateVO;
import com.bogdatech.integration.ALiYunTranslateIntegration;
import com.bogdatech.logic.*;
import com.bogdatech.logic.redis.TranslationParametersRedisService;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.utils.*;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static com.bogdatech.constants.TranslateConstants.METAFIELD;
import static com.bogdatech.logic.redis.TranslationParametersRedisService.generateProgressTranslationKey;
import static com.bogdatech.utils.ApiCodeUtils.getLanguageName;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.JsoupUtils.glossaryText;
import static com.bogdatech.utils.JsoupUtils.isHtml;
import static com.bogdatech.utils.LiquidHtmlTranslatorUtils.isHtmlEntity;
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
    @Autowired
    private ALiYunTranslateIntegration aLiYunTranslateIntegration;

    public String translateHtmlData(String sourceText, RabbitMqTranslateVO vo, CharacterCountUtils counter,
                                    ShopifyRequest shopifyRequest, String source,
                                    Map<String, Object> translation, String resourceId, String translationModel) {
        appInsights.trackTrace("TranslateDataServiceLog translateHtmlData 用户： " + vo.getShopName() + "，sourceText: " + sourceText);

        // 进度条
        translationParametersRedisService.hsetTranslationStatus(
                generateProgressTranslationKey(vo.getShopName(), vo.getSource(), vo.getTarget()), String.valueOf(2));
        translationParametersRedisService.hsetTranslatingString(
                generateProgressTranslationKey(vo.getShopName(), vo.getSource(), vo.getTarget()), sourceText);

        String htmlTranslation;
        try {
            htmlTranslation = liquidHtmlTranslatorUtils.newJsonTranslateHtml(
                    sourceText,
                    new TranslateRequest(0, vo.getShopName(), vo.getAccessToken(), source, vo.getTarget(), sourceText),
                    counter,
                    vo.getLanguagePack(), vo.getLimitChars(), false, translationModel);
            appInsights.trackTrace("TranslateDataServiceLog translateHtmlData 完成 用户： " + vo.getShopName() + "，sourceText: " + sourceText +
                    " translatedText: " + htmlTranslation);
            if (vo.getModeType().equals(METAFIELD)) {
                // TODO 这里是不是不会走到了？
                // 对翻译后的html做格式处理
                appInsights.trackTrace("html所在模块是METAFIELD 用户： " + vo.getShopName() + "，sourceText: " + sourceText);
                htmlTranslation = normalizeHtml(htmlTranslation);
            }
        } catch (Exception e) {
            appInsights.trackTrace("clickTranslation " + vo.getShopName() + " html translation errors : " +
                    e.getMessage() + " sourceText: " + sourceText);
            shopifyService.saveToShopify(sourceText, translation, resourceId, shopifyRequest);
            return null;
        }

        appInsights.trackTrace("存到shopify数据到数据库 用户： " + vo.getShopName() + "，sourceText: " + sourceText);
        return htmlTranslation;
    }

    public String translateListSingleData(String value, String target, RabbitMqTranslateVO vo,
                                          CharacterCountUtils counter, String shopName, ShopifyRequest shopifyRequest, String source,
                                          Map<String, Object> translation, String resourceId) {
        appInsights.trackTrace("TranslateDataServiceLog ListSingleData 用户： " + vo.getShopName() + "，sourceText: " + value);
        // 如果符合要求，则翻译，不符合要求则返回原值
        List<String> resultList = JsonUtils.jsonToObjectWithNull(value, new TypeReference<>() {});
        if (resultList == null || resultList.isEmpty()) {
            return value;
        }
        try {
            for (int i = 0; i < resultList.size(); i++) {
                String original = resultList.get(i);
                if (!StringUtils.isValidString(original) && original != null && !original.trim().isEmpty() && !isHtml(value)) {
                    // 走翻译流程
                    String targetCache = redisProcessService.getCacheData(target, value);
                    if (targetCache != null) {
                        resultList.set(i, targetCache);
                        continue;
                    }
                    translationParametersRedisService.hsetTranslationStatus(generateProgressTranslationKey(shopName,
                            vo.getSource(), vo.getTarget()), String.valueOf(2));
                    translationParametersRedisService.hsetTranslatingString(generateProgressTranslationKey(shopName,
                            vo.getSource(), vo.getTarget()), value);

                    String translated = jsoupUtils.translateByModel(
                            new TranslateRequest(0, shopName, shopifyRequest.getAccessToken(), source, shopifyRequest.getTarget(), value),
                            counter, vo.getLanguagePack(), vo.getLimitChars(), false);

                    // 对null的处理
                    if (translated == null) {
                        appInsights.trackTrace("FatalException 每日须看 translateMetafieldTextData 用户： " + shopName + " 翻译失败，翻译内容为空 value: " + value);
                        translated = jsoupUtils.checkTranslationModel(
                                new TranslateRequest(0, shopName, shopifyRequest.getAccessToken(), source, shopifyRequest.getTarget(), value),
                                counter, vo.getLanguagePack(), vo.getLimitChars(), false);
                        resultList.set(i, translated);
                        continue;
                    }
                    redisProcessService.setCacheData(target, translated, value);
                    //将数据填回去
                    resultList.set(i, translated);
                }
            }
            appInsights.trackTrace("TranslateDataServiceLog ListSingleData 成功 用户： " + vo.getShopName() + "，sourceText: " + value +
                    " translatedText: " + resultList);
            return JsonUtils.objectToJson(resultList);
        } catch (Exception e) {
            //存原数据到shopify本地
            shopifyService.saveToShopify(value, translation, resourceId, shopifyRequest);
            appInsights.trackTrace("clickTranslation " + shopName + " LIST errors 错误原因： " + e.getMessage());
        }
        return null;
    }

    public String translateGlossaryData(String value, RabbitMqTranslateVO vo, CharacterCountUtils counter,
                                        ShopifyRequest shopifyRequest, String source,
                                        Map<String, Object> translation, String resourceId, Integer limitChars,
                                        Map<String, String> keyMap0, Map<String, String> keyMap1) {
        appInsights.trackTrace("TranslateDataServiceLog translateGlossaryData 用户： " + vo.getShopName() + "，sourceText: " + value);

        String languagePack = vo.getLanguagePack();

        TranslateRequest translateRequest = new TranslateRequest(0, shopifyRequest.getShopName(), shopifyRequest.getAccessToken(), source, shopifyRequest.getTarget(), value);

        String targetText;
        // 判断是否为HTML
        if (isHtml(value)) {
            try {
                targetText = jsoupUtils.translateGlossaryHtml(value, translateRequest, counter, null, keyMap0, keyMap1, languagePack, limitChars, false);
                targetText = isHtmlEntity(targetText);
            } catch (Exception e) {
                appInsights.trackTrace("FatalException translateGlossaryData is html failed " + shopifyRequest.getShopName() + " glossaryTranslationModel finalText is null " + " sourceText: " + value);
                shopifyService.saveToShopify(value, translation, resourceId, shopifyRequest);
                return null;
            }

            appInsights.trackTrace("TranslateDataServiceLog translateGlossaryData isHtml 成功 用户： " + vo.getShopName() +
                    "，sourceText: " + value + " translatedText: " + targetText);
            return targetText;
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
                appInsights.trackTrace("FatalException 每日须看 clickTranslation " + shopifyRequest.getShopName() + " glossaryTranslationModel finalText is null " + " sourceText: " + value);
                return null;
            }
        } catch (Exception e) {
            appInsights.trackTrace("clickTranslation " + shopifyRequest.getShopName() + " glossaryTranslationModel errors " + e + " sourceText: " + value);
            shopifyService.saveToShopify(value, translation, resourceId, shopifyRequest);
        }
        appInsights.trackTrace("TranslateDataServiceLog translateGlossaryData notHtml 成功 用户： " + vo.getShopName() +
                "，sourceText: " + value + " translatedText: " + finalText);
        return finalText;
    }

    public Map<String, String> translatePlainText(List<String> untranslatedTexts, RabbitMqTranslateVO vo,
                                                  CharacterCountUtils counter, String shopName, String source,
                                                  Integer limitChars, String translationKeyType,
                                                  TranslateRequest translateRequestTemplate) {
        appInsights.trackTrace("TranslateDataServiceLog PlainText 用户： " + vo.getShopName() + "，sourceText: " + untranslatedTexts);

        if (untranslatedTexts.isEmpty()) {
            return new HashMap<>();
        }
        // 根据不同的key类型，生成对应提示词，后翻译
        String prompt = PlaceholderUtils.getListPrompt(getLanguageName(vo.getTarget()), vo.getLanguagePack(), translationKeyType, vo.getModeType());
        appInsights.trackTrace(shopName + " translatePlainTextData 翻译类型 : " + translationKeyType + " 提示词 : " + prompt + " 未翻译文本 : " + untranslatedTexts);

        String untranslatedTextsJson = JsonUtils.objectToJson(untranslatedTexts);
        String translatedJson = jsoupUtils.translateByCiwiOrGptModel(translateRequestTemplate.getTarget(), untranslatedTextsJson,
                translateRequestTemplate.getShopName(), translateRequestTemplate.getSource(), counter, limitChars, prompt,
                false, vo.getTranslationModel());

        // 如果主翻译服务 translateBatch 返回 null，则使用阿里云翻译服务作为备用
        if (translatedJson == null) {
            translatedJson = aLiYunTranslateIntegration.userTranslate(untranslatedTextsJson, prompt, counter, vo.getTarget(), shopName, limitChars, false);
        }

        appInsights.trackTrace("TranslateDataServiceLog PlainText 用户： " + vo.getShopName() + "，sourceText: " + untranslatedTexts
                + " translatedJson: " + translatedJson);

        if (translatedJson == null) {
            appInsights.trackTrace("FatalException TranslateDataServiceLog translatePlainTextData 用户： " + shopName +
                    " 翻译失败，map为空 untranslatedTexts: " + untranslatedTexts + " 返回值: " + translatedJson);
            return new HashMap<>();
        }

        Map<String, String> map = JsonUtils.jsonToObjectWithNull(translatedJson, new TypeReference<Map<String, String>>() {});
        if (map == null) {
            appInsights.trackTrace("FatalException TranslateDataServiceLog translatePlainTextData 用户： " + shopName +
                    " 翻译失败，map为空 untranslatedTexts: " + untranslatedTexts + " 返回值: " + translatedJson);
            return new HashMap<>();
        }
        return map;
    }
}
