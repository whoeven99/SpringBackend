package com.bogdatech.logic;

import com.bogdatech.Service.IAILanguagePacksService;
import com.bogdatech.entity.DO.TranslateResourceDTO;
import com.bogdatech.integration.HunYuanIntegration;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.utils.CharacterCountUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.bogdatech.constants.TranslateConstants.*;
import static com.bogdatech.logic.TranslateService.getShopifyData;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.PlaceholderUtils.getCategoryPrompt;

@Component
public class AILanguagePackService {
    private final IAILanguagePacksService aiLanguagePacksService;
    private final HunYuanIntegration hunYuanIntegration;

    @Autowired
    public AILanguagePackService(IAILanguagePacksService aiLanguagePacksService, HunYuanIntegration hunYuanIntegration) {
        this.aiLanguagePacksService = aiLanguagePacksService;
        this.hunYuanIntegration = hunYuanIntegration;
    }

    /**
     * 获取用户的beta_description，根据这个由混元生成类目
     * @param shopName 店铺名称
     * @param accessToken 店铺token
     * return true or false
     */
    public String getCategoryByDescription(String shopName, String accessToken, CharacterCountUtils counter, Integer limitChars, String target) {
        //先判断数据库中是否有数据
        String languagePackId = aiLanguagePacksService.getLanguagePackByShopName(shopName);
        if (languagePackId != null && !languagePackId.isEmpty()) {
            return languagePackId;
        }

        String shopifyData = getShopifyData(new ShopifyRequest(shopName, accessToken, API_VERSION_LAST, "zh-CN"), new TranslateResourceDTO(SHOP, MAX_LENGTH, "zh-CN", ""));
        if (shopifyData == null || shopifyData.isEmpty()) {
            return null;
        }

        String description = getDescription(shopifyData);
        if (description == null || description.isEmpty()) {
            return null;
        }
        //判断description是否为空
        //调用混元生成类目
        String categoryPrompt = getCategoryPrompt();
        String categoryText = hunYuanIntegration.hunYuanTranslate(description, categoryPrompt, counter, HUN_YUAN_MODEL, shopName, limitChars, target, false);
        if (categoryText == null || categoryText.isEmpty()) {
            appInsights.trackTrace("每日须看 getCategoryByDescription " + shopName + " description: " + description + "生成的类目数据为空");
            return null;
        }
        appInsights.trackTrace("getCategoryByDescription " + shopName + " category counter: " + counter.getTotalChars() + "生成的类目数据为： " + categoryText);
        //如果categoryText字段在255字符里面，存到数据库中
        if (categoryText.length() > 100) {
            return categoryText;
        }
        //存到数据库中
        aiLanguagePacksService.insertOrUpdateCategory(shopName, categoryText);
        return categoryText;
    }

    /**
     * 解析shopify获取的数据，获取description
     */
    public static String getDescription(String shopifyData) {
        try {
            JsonNode nodes = new ObjectMapper()
                    .readTree(shopifyData)
                    .path("translatableResources")
                    .path("nodes");

            if (!nodes.isArray()) {
                return null;
            }

            for (JsonNode node : nodes) {
                JsonNode translatableContent = node.path("translatableContent");
                if (!translatableContent.isArray()) continue;

                for (JsonNode item : translatableContent) {
                    if ("meta_description".equals(item.path("key").asText(null))) {
                        return item.path("value").asText(null);
                    }
                }
            }

        } catch (JsonProcessingException e) {
            appInsights.trackTrace("JSON 解析错误: " + e);
        } catch (Exception e) {
            appInsights.trackTrace("处理过程中抛出异常: " + e);
        }

        return null;
    }
}
