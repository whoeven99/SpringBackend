package com.bogdatech.logic;

import com.bogdatech.Service.IAILanguagePacksService;
import com.bogdatech.entity.DO.TranslateResourceDTO;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.utils.CharacterCountUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.bogdatech.constants.TranslateConstants.MAX_LENGTH;
import static com.bogdatech.constants.TranslateConstants.SHOP;
import static com.bogdatech.integration.HunYuanIntegration.hunYuanTranslate;
import static com.bogdatech.logic.TranslateService.getShopifyData;
import static com.bogdatech.utils.PlaceholderUtils.getCategoryPrompt;

@Component
public class AILanguagePackService {
    private final IAILanguagePacksService aiLanguagePacksService;

    @Autowired
    public AILanguagePackService(IAILanguagePacksService aiLanguagePacksService) {
        this.aiLanguagePacksService = aiLanguagePacksService;
    }

    /**
     * 获取用户的beta_description，根据这个由混元生成类目
     * @param shopName 店铺名称
     * @param accessToken 店铺token
     * return true or false
     */
    public Boolean getCategoryByDescription(String shopName, String accessToken, CharacterCountUtils counter) {
        String shopifyData = getShopifyData(new ShopifyRequest(shopName, accessToken, "2024-10", "zh-CN"), new TranslateResourceDTO(SHOP, MAX_LENGTH, "zh-CN", ""));
        if (shopifyData == null) {
            return false;
        }

        String description = getDescription(shopifyData);
        System.out.println("description: " + description);
        if (description != null && !description.trim().isEmpty()){
            return true;
        }

        //判断description是否为空
        //调用混元生成类目
        String categoryPrompt = getCategoryPrompt();
        String categoryText = hunYuanTranslate(description, categoryPrompt, counter, null, "hunyuan-large");
        System.out.println("category counter: " + counter.getTotalChars());
        System.out.println("categoryText: " + categoryText);
        //如果categoryText字段在255字符里面，存到数据库中
        if (categoryText.length() > 100) {
            return false;
        }

        //存到数据库中
        return aiLanguagePacksService.insertOrUpdateCategory(shopName, categoryText);
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
            System.out.println("JSON 解析错误: " + e);
        } catch (Exception e) {
            System.out.println("处理过程中抛出异常: " + e);
        }

        return null;
    }
}
