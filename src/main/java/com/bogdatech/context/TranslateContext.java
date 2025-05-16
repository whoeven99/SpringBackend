package com.bogdatech.context;

import com.bogdatech.entity.TranslateResourceDTO;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.utils.CharacterCountUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
@AllArgsConstructor
@NoArgsConstructor
@Data
public class TranslateContext {
    private String shopifyData;
    private ShopifyRequest shopifyRequest;
    private TranslateResourceDTO translateResource;
    private CharacterCountUtils characterCountUtils;
    private Integer remainingChars;
    private Map<String, Object> glossaryMap;
    private String source;
    private String languagePackId;
    private String apiKey;
}
