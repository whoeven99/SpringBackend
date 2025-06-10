package com.bogdatech.logic;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bogdatech.Service.IAPGTemplateService;
import com.bogdatech.Service.IAPGUserCounterService;
import com.bogdatech.Service.IAPGUsersService;
import com.bogdatech.entity.DO.APGUserCounterDO;
import com.bogdatech.entity.DO.APGUsersDO;
import com.bogdatech.entity.VO.GenerateDescriptionVO;
import com.bogdatech.model.controller.request.CloudServiceRequest;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.utils.CharacterCountUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.bogdatech.constants.TranslateConstants.HUN_YUAN_MODEL;
import static com.bogdatech.integration.HunYuanIntegration.hunYuanUserTranslate;
import static com.bogdatech.integration.ShopifyHttpIntegration.getInfoByShopify;
import static com.bogdatech.requestBody.ShopifyRequestBody.getCollectionsQueryById;
import static com.bogdatech.requestBody.ShopifyRequestBody.getProductsQueryById;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.GenerateDescriptionUtils.*;

@Service
public class GenerateDescriptionService {

    private final IAPGTemplateService iapgTemplateService;
    private final IAPGUsersService iapgUsersService;
    private final IAPGUserCounterService iapgUserCounterService;

    @Autowired
    public GenerateDescriptionService(IAPGTemplateService iapgTemplateService, IAPGUsersService iapgUsersService, IAPGUserCounterService iapgUserCounterService) {
        this.iapgTemplateService = iapgTemplateService;
        this.iapgUsersService = iapgUsersService;
        this.iapgUserCounterService = iapgUserCounterService;
    }

    /**
     * 生成产品描述
     *
     * @param shopName              店铺名称
     * @param generateDescriptionVO 生成描述参数
     * @return 产品描述
     */
    public String generateDescription(String shopName, GenerateDescriptionVO generateDescriptionVO) {
        // 生成产品描述(简单的做，尝试一下) 用混元先生成
        //根据shopName获取accessToken数据
        APGUsersDO userDO = iapgUsersService.getOne(new QueryWrapper<APGUsersDO>().eq("shop_name", shopName));
        APGUserCounterDO counterDO = iapgUserCounterService.getOne(new QueryWrapper<APGUserCounterDO>().eq("shop_name", shopName));
        //根据pageType和contentType决定使用什么方法
        String query = switch (generateDescriptionVO.getPageType()) {
            case "product" -> getProductsQueryById(generateDescriptionVO.getId());
            case "collection" -> getCollectionsQueryById(generateDescriptionVO.getId());
            default -> null;
        };
        //根据id获取产品具体信息
        if (query == null) {
            return "not generate";
        }
        String shopifyData = null;
        try {
            String env = System.getenv("ApplicationEnv");
            if ("prod".equals(env) || "dev".equals(env)) {
                shopifyData = String.valueOf(getInfoByShopify(new ShopifyRequest(shopName, userDO.getAccessToken(), "2025-04", null), query));
            } else {
                shopifyData = ShopifyService.getShopifyData(new CloudServiceRequest(shopName, userDO.getAccessToken(), "2025-04", null, query));
            }
        } catch (Exception e) {
            // 如果出现异常，则跳过, 翻译其他的内容
            //更新当前字符数
            appInsights.trackTrace("Failed to get Shopify data: " + e.getMessage());
        }
        //解析shopifyData，获取title等数据
        String title = parseShopifyData(shopifyData, generateDescriptionVO.getPageType());
        if (title == null) {
            return null;
        }
        String prompt = null;
        String template = null;
        //根据contentType判断用seo还是des
        switch (generateDescriptionVO.getContentType()) {
            case "Description" :
                Long templateId = Long.parseLong(generateDescriptionVO.getTemplateId());
                template = iapgTemplateService.getTemplateById(templateId);
                prompt = generatePrompt(title, generateDescriptionVO.getLanguage());
                template = prompt + template;
                break;
            case "SEODescription" :
                template = generateSeoPrompt(title, generateDescriptionVO.getLanguage());
                break;
        };
        if (template == null ){
            return "des/seo not generate";
        }
//        String prompt = generatePrompt(title, generateDescriptionVO.getLanguage());
        //根据seoKeyword（暂时不知道是什么）
        //根据test的的boolean值 true，使用的是templateId传递的模板数据；false，使用的是templateId传递的字符型数字
        boolean test = Boolean.parseBoolean(generateDescriptionVO.getTest());
        String description;
        CharacterCountUtils characterCountUtils = new CharacterCountUtils();
        if (test) {
            String templateId = generateDescriptionVO.getTemplateId();
            description = hunYuanUserTranslate(templateId, characterCountUtils, HUN_YUAN_MODEL);
        } else {
            // 从数据库中获取(暂定就模板1)
            description = hunYuanUserTranslate(template, characterCountUtils, HUN_YUAN_MODEL);
        }
        //根据additionalInformation（暂时不知道是什么）
        //根据language获取生成的语言是什么
        //根据model选择对应的模型（暂定混元）
        //将消耗的数据记录到数据库中，目前都是产品
        boolean update = iapgUserCounterService.update(new UpdateWrapper<APGUserCounterDO>().eq("shop_name", shopName)
                .set("user_token", counterDO.getUserToken() + characterCountUtils.getTotalChars())
                .set("product_counter", counterDO.getProductCounter() + 1));
        if (!update) {
            //更新失败，记录日志 \
            appInsights.trackTrace("更新失败: " + shopName + " userToken: " + characterCountUtils.getTotalChars());
        }

        return description;
    }
}
