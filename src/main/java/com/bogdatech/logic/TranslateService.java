package com.bogdatech.logic;

import com.bogdatech.integration.TranslateApiIntegration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TranslateService {

    @Autowired
    private TranslateApiIntegration translateApiIntegration;

    public String translate(String text) {
        return translateApiIntegration.translateText(text);
    }
}
