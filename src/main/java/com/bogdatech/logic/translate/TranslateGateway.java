package com.bogdatech.logic.translate;

import com.bogdatech.integration.ALiYunTranslateIntegration;
import com.bogdatech.integration.ChatGptIntegration;
import com.bogdatech.integration.TranslateApiIntegration;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TranslateGateway {
    @Autowired
    private ALiYunTranslateIntegration aLiYunTranslateIntegration;
    @Autowired
    private TranslateApiIntegration translateApiIntegration;
    @Autowired
    private ChatGptIntegration chatGptIntegration;

    // 根据需要选择不同的翻译服务
    public Pair<String, Integer> translate(String prompt, String targetLanguage, String privateKey, Integer translateModelFlag) {
        if (privateKey != null) {
            return chatGptIntegration.chatWithGpt(prompt, null, null, targetLanguage, privateKey);
        }
    }
}
