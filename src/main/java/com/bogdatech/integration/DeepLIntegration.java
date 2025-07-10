package com.bogdatech.integration;

import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.utils.CharacterCountUtils;
import com.deepl.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.Map;
import static com.bogdatech.logic.TranslateService.userTranslate;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.MapUtils.getTranslationStatusMap;

@Component
public class DeepLIntegration {
    private static final String API_KEY = "";
    private static final String FREE_API_URL = "https://api-free.deepl.com/v2/translate";
    private static final String API_URL = "https://api.deepl.com/v2/translate";
    private final ITranslationCounterService translationCounterService;
    DeepLClient client;

    @Autowired
    public DeepLIntegration(ITranslationCounterService translationCounterService) {
        this.translationCounterService = translationCounterService;
    }

    public String translateByDeepL(String sourceText , String targetCode, CharacterCountUtils counter, String shopName, Integer limitChars) {
        client = new DeepLClient(API_KEY);
        TextResult result = null;
        try {
            result = client.translateText(sourceText, null, targetCode);
            String targetText = result.getText();
            int totalToken = result.getBilledCharacters();
            System.out.println("翻译的文本： " + targetText);
            System.out.println("消耗的字符： " + result.getBilledCharacters());
            counter.addChars(totalToken);
            appInsights.trackTrace( "用户： " + shopName +" token deepL : " + targetText + " all: " + totalToken);
            Map<String, Object> translationStatusMap = getTranslationStatusMap(sourceText, 2);
            userTranslate.put(shopName, translationStatusMap);
            translationCounterService.updateAddUsedCharsByShopName(shopName, totalToken, limitChars);
            return targetText;
        } catch (Exception e) {
            appInsights.trackTrace("DeepL翻译失败 errors : " + e);
        }
        return sourceText;
    }

}
