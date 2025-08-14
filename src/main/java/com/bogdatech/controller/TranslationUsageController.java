package com.bogdatech.controller;

import com.bogdatech.Service.ITranslationUsageService;
import com.bogdatech.entity.DO.TranslationUsageDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@RestController
@RequestMapping("/translationUsage")
public class TranslationUsageController {

    private final ITranslationUsageService translationUsageService;

    @Autowired
    public TranslationUsageController(ITranslationUsageService translationUsageService) {
        this.translationUsageService = translationUsageService;
    }

    @PostMapping("/getTranslationUsage")
    public String getTranslationUsage() {
        String shopName = "ciwishop.myshopify.com";
        List<TranslationUsageDO> translationUsageDOS = translationUsageService.readTranslationUsageData(shopName);
        appInsights.trackTrace("translationUsageDOS: " + translationUsageDOS);
        return "getTranslationUsage";
    }
}
