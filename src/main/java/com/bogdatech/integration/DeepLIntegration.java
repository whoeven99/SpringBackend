package com.bogdatech.integration;

import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.utils.CharacterCountUtils;
import com.deepl.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static com.bogdatech.constants.TranslateConstants.*;
import static com.bogdatech.logic.TranslateService.userTranslate;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.MapUtils.getTranslationStatusMap;

@Component
public class DeepLIntegration {
    private static final String API_KEY = System.getenv(DEEPL_API_KEY);
    private static final String FREE_API_URL = "https://api-free.deepl.com/v2/translate";
    private static final String API_URL = "https://api.deepl.com/v2/translate";
    private final ITranslationCounterService translationCounterService;
    DeepLClient client;
    private final int MAX_LIMIT = 500000;

    @Autowired
    public DeepLIntegration(ITranslationCounterService translationCounterService) {
        this.translationCounterService = translationCounterService;
    }

    /**
     * 使用deepL进行翻译,计数翻译数
     * */
    public String translateByDeepL(String sourceText , String targetCode, CharacterCountUtils counter, String shopName, Integer limitChars) {
        //target要做映射
        client = new DeepLClient(API_KEY);
        TextResult result = null;
        try {
            String target = DEEPL_LANGUAGE_MAP.get(targetCode);
            result = client.translateText(sourceText, null, target);
            appInsights.trackTrace("result: " + result);
            String targetText = result.getText();
            int totalToken = result.getBilledCharacters() * DEEPL_MAGNIFICATION;
            appInsights.trackTrace( "clickTranslation translateByDeepL 用户： " + shopName  + "翻译的文本： " + sourceText + " token deepL : " + targetText + " all: " + totalToken);
            Map<String, Object> translationStatusMap = getTranslationStatusMap(sourceText, 2);
            userTranslate.put(shopName, translationStatusMap);
            translationCounterService.updateAddUsedCharsByShopName(shopName, totalToken, limitChars);
            counter.addChars(totalToken);
            return targetText;
        } catch (Exception e) {
            appInsights.trackTrace("clickTranslation translateByDeepL DeepL翻译失败 errors : " + e);
            appInsights.trackException(e);
        }
        return sourceText;
    }

    /**
     * 用于用户deepL翻译
     * */
    public String privateTranslateByDeepL(String sourceText , String targetCode, CharacterCountUtils counter, String shopName, Integer limitChars, DeepLClient client) {
        TextResult result;
        try {
            String target = DEEPL_LANGUAGE_MAP.get(targetCode);
            result = client.translateText(sourceText, null, target);
            String targetText = result.getText();
            int totalToken = result.getBilledCharacters() * DEEPL_MAGNIFICATION;
            appInsights.trackTrace( "translateByDeepL 用户： " + shopName  + "翻译的文本： " + sourceText + " token deepL : " + targetText + " all: " + totalToken);
            Map<String, Object> translationStatusMap = getTranslationStatusMap(sourceText, 2);
            userTranslate.put(shopName, translationStatusMap);
            translationCounterService.updateAddUsedCharsByShopName(shopName, totalToken, limitChars);
            counter.addChars(totalToken);
            return targetText;
        } catch (Exception e) {
            appInsights.trackTrace("DeepL翻译失败 errors : " + e);
        }
        return sourceText;
    }


    /**
     * 在翻译前判断deepL额度是否足够,是继续翻译,不是改为ciwi翻译
     * */
    public Boolean isDeepLEnough(){
        client = new DeepLClient(API_KEY);
        try {
            Usage usage = client.getUsage();
            appInsights.trackTrace("DeepL额度: " + usage.getCharacter());
            return usage.getCharacter().getCount() > MAX_LIMIT;
        } catch (Exception e) {
            appInsights.trackTrace("DeepL额度查询失败 errors : " + e);
            return true;
        }
    }

    /**
     * deepL的语言映射规则
     * */
    public static final Map<String, String> DEEPL_LANGUAGE_MAP = new HashMap<>();
    static {
        // 映射初始化
        DEEPL_LANGUAGE_MAP.put("ar", "AR");
        DEEPL_LANGUAGE_MAP.put("bg", "BG");
        DEEPL_LANGUAGE_MAP.put("cs", "CS");
        DEEPL_LANGUAGE_MAP.put("da", "DA");
        DEEPL_LANGUAGE_MAP.put("de", "DE");
        DEEPL_LANGUAGE_MAP.put("el", "EL");
        DEEPL_LANGUAGE_MAP.put("en", "EN-US"); // 建议用 en-GB 或 en-US 替代
        DEEPL_LANGUAGE_MAP.put("es", "ES");
        DEEPL_LANGUAGE_MAP.put("et", "ET");
        DEEPL_LANGUAGE_MAP.put("fi", "FI");
        DEEPL_LANGUAGE_MAP.put("fr", "FR");
        DEEPL_LANGUAGE_MAP.put("he", "HE");
        DEEPL_LANGUAGE_MAP.put("hu", "HU");
        DEEPL_LANGUAGE_MAP.put("id", "ID");
        DEEPL_LANGUAGE_MAP.put("it", "IT");
        DEEPL_LANGUAGE_MAP.put("ja", "JA");
        DEEPL_LANGUAGE_MAP.put("ko", "KO");
        DEEPL_LANGUAGE_MAP.put("lt", "LT");
        DEEPL_LANGUAGE_MAP.put("lv", "LV");
        DEEPL_LANGUAGE_MAP.put("nb", "NB");
        DEEPL_LANGUAGE_MAP.put("nl", "NL");
        DEEPL_LANGUAGE_MAP.put("pl", "PL");
        DEEPL_LANGUAGE_MAP.put("pt-BR", "PT-BR");
        DEEPL_LANGUAGE_MAP.put("pt-PT", "PT-PT");
        DEEPL_LANGUAGE_MAP.put("ro", "RO");
        DEEPL_LANGUAGE_MAP.put("ru", "RU");
        DEEPL_LANGUAGE_MAP.put("sk", "SK");
        DEEPL_LANGUAGE_MAP.put("sl", "SL");
        DEEPL_LANGUAGE_MAP.put("sv", "SV");
        DEEPL_LANGUAGE_MAP.put("th", "TH");
        DEEPL_LANGUAGE_MAP.put("tr", "TR");
        DEEPL_LANGUAGE_MAP.put("uk", "UK");
        DEEPL_LANGUAGE_MAP.put("vi", "VI");
        DEEPL_LANGUAGE_MAP.put("zh-CN", "ZH-HANS");
        DEEPL_LANGUAGE_MAP.put("zh-TW", "ZH-HANT");
    }
}
