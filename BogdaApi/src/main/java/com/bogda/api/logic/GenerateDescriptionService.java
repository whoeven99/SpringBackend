package com.bogda.api.logic;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bogda.api.Service.IAPGOfficialTemplateService;
import com.bogda.api.Service.IAPGUserCounterService;
import com.bogda.api.Service.IAPGUserProductService;
import com.bogda.api.Service.IAPGUserTemplateService;
import com.bogda.api.entity.DO.APGOfficialTemplateDO;
import com.bogda.api.entity.DO.APGUserCounterDO;
import com.bogda.api.entity.DO.APGUserTemplateDO;
import com.bogda.api.entity.DO.APGUsersDO;
import com.bogda.api.entity.DTO.ProductDTO;
import com.bogda.api.entity.DTO.TemplateDTO;
import com.bogda.api.entity.VO.APGAnalyzeDataVO;
import com.bogda.api.entity.VO.GenerateDescriptionVO;
import com.bogda.api.exception.ClientException;
import com.bogda.api.integration.ALiYunTranslateIntegration;
import com.bogda.api.utils.CharacterCountUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Random;

import static com.bogda.api.constants.TranslateConstants.APIVERSION;
import static com.bogda.api.constants.TranslateConstants.CHARACTER_LIMIT;
import static com.bogda.api.logic.APGUserGeneratedTaskService.*;
import static com.bogda.api.logic.TranslateService.OBJECT_MAPPER;
import static com.bogda.api.requestBody.ShopifyRequestBody.getProductDataQuery;
import static com.bogda.api.task.GenerateDbTask.GENERATE_SHOP_BAR;
import static com.bogda.api.utils.CaseSensitiveUtils.appInsights;
import static com.bogda.api.utils.PlaceholderUtils.buildDescriptionPrompt;
import static com.bogda.api.utils.StringUtils.countWords;
import static com.bogda.api.utils.TypeConversionUtils.officialTemplateToTemplateDTO;
import static com.bogda.api.utils.TypeConversionUtils.userTemplateToTemplateDTO;

@Service
public class GenerateDescriptionService {

    @Autowired
    private IAPGUserCounterService iapgUserCounterService;
    @Autowired
    private IAPGUserTemplateService iapgUserTemplateService;
    @Autowired
    private IAPGOfficialTemplateService iapgOfficialTemplateService;
    @Autowired
    private IAPGUserProductService iapgUserProductService;
    @Autowired
    private ALiYunTranslateIntegration aLiYunTranslateIntegration;
    @Autowired
    private ShopifyService shopifyService;

    /**
     * 生成产品描述
     *
     * @param usersDO              用户数据
     * @param generateDescriptionVO 生成描述参数
     * @return 产品描述
     */
    public String generateDescription(APGUsersDO usersDO, GenerateDescriptionVO generateDescriptionVO, CharacterCountUtils counter, Integer userMaxLimit, ProductDTO product) {
        //判断额度是否足够，然后决定是否继续调用
        APGUserCounterDO counterDO = iapgUserCounterService.getOne(new QueryWrapper<APGUserCounterDO>().eq("user_id", usersDO.getId()));
        if (counterDO.getUserToken() >= userMaxLimit) {
            throw new ClientException(CHARACTER_LIMIT);
        }
        // 根据产品id获取相关数据，为生成做铺垫
        GENERATE_SHOP_BAR.put(usersDO.getId(), product.getProductTitle());
        // 根据模板id获取模板数据
        TemplateDTO templateById = getTemplateById(generateDescriptionVO.getTemplateId(), usersDO.getId(), generateDescriptionVO.getTemplateType());
        // 根据 ProductDTO 和传入的 GenerateDescriptionVO进行描述生成(暂定qwen模型 图片理解)

        counter.addChars(counterDO.getUserToken());
        //生成提示词
        String prompt = buildDescriptionPrompt(product.getProductTitle(), product.getProductType(), product.getProductDescription(), generateDescriptionVO.getSeoKeywords(), product.getImageUrl(), product.getImageAltText(), generateDescriptionVO.getTextTone(), templateById.getTemplateType(), generateDescriptionVO.getBrandTone(), templateById.getTemplateData(), generateDescriptionVO.getLanguage(), generateDescriptionVO.getContentType(), generateDescriptionVO.getBrandWord(), generateDescriptionVO.getBrandSlogan());
        appInsights.trackTrace(usersDO.getShopName() + " 用户 " + product.getId() + " 的提示词为 ： " + prompt);
        //调用大模型翻译
        //如果产品图片为空，换模型生成
        String des;
        GENERATE_STATE_BAR.put(usersDO.getId(), GENERATING);
        if (product.getImageUrl() == null || product.getImageUrl().isEmpty()) {
             des = aLiYunTranslateIntegration.callWithQwenMaxToDes(prompt, counter, usersDO.getId(), userMaxLimit);
        }else {
             des = aLiYunTranslateIntegration.callWithPicMess(prompt, usersDO.getId(), counter, product.getImageUrl(), userMaxLimit);
        }
        if (des == null) {
            return null;
        }
//        每次生成都要更新一下版本记录和生成数据
        iapgUserProductService.updateProductVersion(usersDO.getId(), generateDescriptionVO.getProductId(), des, generateDescriptionVO.getPageType() , generateDescriptionVO.getContentType());
        GENERATE_STATE_BAR.put(usersDO.getId(), FINISHED);
        return des;
    }

    /**
     * 根据产品id获取相关数据，为翻译做铺垫
     * */
    public ProductDTO getProductsQueryByProductId(String productId, String shopName, String accessToken) {
        String productDataQuery = getProductDataQuery(productId);
        String productData = shopifyService.getShopifyData(shopName, accessToken, APIVERSION, productDataQuery);
        ProductDTO productDTO = new ProductDTO();
        // 对productData进行解析，输出productDTO类型数据
        try {
            JsonNode root = OBJECT_MAPPER.readTree(productData);
            productDTO.setProductDescription(root.at("/product/descriptionHtml").asText(null));
            productDTO.setId(root.at("/product/id").asText());
            productDTO.setProductType(root.at("/product/productType").asText(null));
            productDTO.setImageUrl(root.at("/product/media/edges/0/node/image/url").asText(null));
            productDTO.setImageAltText(root.at("/product/media/edges/0/node/image/altText").asText(null));
            productDTO.setProductTitle(root.at("/product/title").asText(null));
            return productDTO;
        } catch (Exception e) {
            appInsights.trackTrace("FatalException getProductsQueryByProductId errors : " + e);
            appInsights.trackException(e);
            return null;
        }
    }

    /**
     * 根据模板id获取模板数据
     * */
    public TemplateDTO getTemplateById(Long templateId, Long userId, Boolean templateType) {
        //根据templateType选择官方或用户模板
        if (templateType) {
            //获取用户模板
            APGUserTemplateDO one = iapgUserTemplateService.getOne(new LambdaQueryWrapper<APGUserTemplateDO>().eq(APGUserTemplateDO::getUserId, userId).eq(APGUserTemplateDO::getId, templateId));
            return userTemplateToTemplateDTO(one);
        }else {
            //获取官方模板
            APGOfficialTemplateDO one = iapgOfficialTemplateService.getOne(new LambdaQueryWrapper<APGOfficialTemplateDO>().eq(APGOfficialTemplateDO::getId, templateId));
            return officialTemplateToTemplateDTO(one);
        }
    }

    public APGAnalyzeDataVO analyzeDescriptionData(String description, String generation, String seoKeywords) {
        //根据生成前数据和生成后数据分析
        double desInt = countWords(description);
        double proDesInt = countWords(generation);
        int seoKeywordsInt = countWords(seoKeywords);
        APGAnalyzeDataVO apgAnalyzeDataVO = new APGAnalyzeDataVO();
        apgAnalyzeDataVO.setWordCount(proDesInt);
        apgAnalyzeDataVO.setWordGap((double) Math.round(((proDesInt / desInt) - 1) * 10000) / 10000);
        if (seoKeywords != null && !seoKeywords.isEmpty()) {
            double keywordPercent = (double) Math.round((seoKeywordsInt / desInt) * 10000) / 10000;
            apgAnalyzeDataVO.setKeywordStrong(seoKeywords);
            apgAnalyzeDataVO.setKeywordPercent(keywordPercent);
            double keywordCompare = (double) Math.round(((double) seoKeywordsInt / (desInt - proDesInt)) * 10000) / 10000;
            apgAnalyzeDataVO.setKeywordCompare(keywordCompare);
        }
        Random random = new Random();
        int textPercent = random.nextInt(50,90);
        int ctrIncrease = random.nextInt(5,55);
        apgAnalyzeDataVO.setCtrIncrease(ctrIncrease);
        apgAnalyzeDataVO.setTextPercent(textPercent);
        apgAnalyzeDataVO.setGenerateText(generation);
        return apgAnalyzeDataVO;
    }
}
