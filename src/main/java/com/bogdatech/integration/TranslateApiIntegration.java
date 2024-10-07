package com.bogdatech.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TranslateApiIntegration {

    @Autowired
    private BaseHttpIntegration baseHttpIntegration;

    public String translateText(String text) {
        try {
//            var ans = baseHttpIntegration.sendHttpGet("/google/translate");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }
}
