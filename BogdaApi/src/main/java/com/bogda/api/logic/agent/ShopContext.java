package com.bogda.api.logic.agent;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ShopContext {
    private String shopName;
    private String accessToken;
    private List<ProductInfo> products;
    private List<LanguageInfo> languages;
    private Map<String, Object> additionalData;
    
    @Data
    public static class ProductInfo {
        private String id;
        private String title;
        private String description;
        private boolean isTranslated;
        private List<String> translatedLanguages;
    }
    
    @Data
    public static class LanguageInfo {
        private String code;
        private String name;
        private boolean isPrimary;
    }
}
