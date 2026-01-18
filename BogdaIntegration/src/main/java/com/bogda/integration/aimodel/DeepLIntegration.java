package com.bogda.integration.aimodel;

import com.bogda.common.contants.TranslateConstants;
import com.bogda.common.utils.AppInsightsUtils;
import com.bogda.common.utils.CharacterCountUtils;
import com.bogda.common.utils.ConfigUtils;
import com.deepl.api.DeepLClient;
import com.deepl.api.TextResult;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static com.bogda.common.utils.TimeOutUtils.*;

@Component
public class DeepLIntegration {
    private static final String API_KEY = ConfigUtils.getConfig(TranslateConstants.DEEPL_API_KEY);

    DeepLClient client;

    /**
     * 使用deepL进行翻译,计数翻译数
     */
    public String translateByDeepL(String sourceText, String targetCode, CharacterCountUtils counter, String shopName
            , Integer limitChars, boolean isSingleFlag, String translateType) {
        //target要做映射
        client = new DeepLClient(API_KEY);
        TextResult result;
        try {
            String target = DEEPL_LANGUAGE_MAP.get(targetCode);
            result = callWithTimeoutAndRetry(() -> {
                        try {
                            return client.translateText(sourceText, null, target);
                        } catch (Exception e) {
                            AppInsightsUtils.trackTrace("FatalException 每日须看 translateByDeepL deepL翻译报错信息 errors ： " + e.getMessage() + " translateText : " + sourceText + " 用户：" + shopName);
                            AppInsightsUtils.trackException(e);
                            return null;
                        }
                    },
                    DEFAULT_TIMEOUT, DEFAULT_UNIT,    // 超时时间
                    DEFAULT_MAX_RETRIES                // 最多重试3次
            );
            if (result == null) {
                return sourceText;
            }
            AppInsightsUtils.trackTrace("result: " + result);
            String targetText = result.getText();
            int totalToken = result.getBilledCharacters() * TranslateConstants.DEEPL_MAGNIFICATION;
            AppInsightsUtils.printTranslateCost(totalToken, totalToken, totalToken);
            AppInsightsUtils.trackTrace("clickTranslation translateByDeepL 用户： " + shopName + "翻译的文本： " + sourceText + " token deepL : " + targetText + " all: " + totalToken);

            counter.addChars(totalToken);
            return targetText;
        } catch (Exception e) {
            AppInsightsUtils.trackTrace("FatalException clickTranslation translateByDeepL deepL翻译报错信息 errors : " + e + " sourceText: " + sourceText + " targetCode: " + targetCode);
            AppInsightsUtils.trackException(e);
        }
        return sourceText;
    }

    /**
     * deepL的语言映射规则
     */
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
