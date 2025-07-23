package com.bogdatech.logic;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bogdatech.Service.*;
import com.bogdatech.entity.DO.APGOfficialTemplateDO;
import com.bogdatech.entity.DO.APGUserCounterDO;
import com.bogdatech.entity.DO.APGUserTemplateDO;
import com.bogdatech.entity.DO.APGUsersDO;
import com.bogdatech.entity.DTO.ProductDTO;
import com.bogdatech.entity.DTO.TemplateDTO;
import com.bogdatech.entity.VO.GenerateDescriptionVO;
import com.bogdatech.exception.ClientException;
import com.bogdatech.integration.ALiYunTranslateIntegration;
import com.bogdatech.model.controller.request.CloudServiceRequest;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.utils.CharacterCountUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.bogdatech.constants.TranslateConstants.CHARACTER_LIMIT;
import static com.bogdatech.integration.ShopifyHttpIntegration.getInfoByShopify;
import static com.bogdatech.logic.ShopifyService.getShopifyDataByCloud;
import static com.bogdatech.logic.TranslateService.OBJECT_MAPPER;
import static com.bogdatech.requestBody.ShopifyRequestBody.*;
import static com.bogdatech.task.GenerateDbTask.GENERATE_SHOP_BAR;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.PlaceholderUtils.buildDescriptionPrompt;
import static com.bogdatech.utils.TypeConversionUtils.officialTemplateToTemplateDTO;
import static com.bogdatech.utils.TypeConversionUtils.userTemplateToTemplateDTO;

@Service
public class GenerateDescriptionService {

    private final IAPGUserCounterService iapgUserCounterService;
    private final IAPGUserTemplateService iapgUserTemplateService;
    private final IAPGOfficialTemplateService iapgOfficialTemplateService;
    private final IAPGUserProductService iapgUserProductService;
    private final ALiYunTranslateIntegration aLiYunTranslateIntegration;
    @Autowired
    public GenerateDescriptionService(IAPGUserCounterService iapgUserCounterService, IAPGUserTemplateService iapgUserTemplateService, IAPGOfficialTemplateService iapgOfficialTemplateService, IAPGUserProductService iapgUserProductService, ALiYunTranslateIntegration aLiYunTranslateIntegration) {
        this.iapgUserCounterService = iapgUserCounterService;
        this.iapgUserTemplateService = iapgUserTemplateService;
        this.iapgOfficialTemplateService = iapgOfficialTemplateService;
        this.iapgUserProductService = iapgUserProductService;
        this.aLiYunTranslateIntegration = aLiYunTranslateIntegration;
    }

    /**
     * 生成产品描述
     *
     * @param usersDO              用户数据
     * @param generateDescriptionVO 生成描述参数
     * @return 产品描述
     */
    public String generateDescription(APGUsersDO usersDO, GenerateDescriptionVO generateDescriptionVO, CharacterCountUtils counter, Integer userMaxLimit) {
        //判断额度是否足够，然后决定是否继续调用
        APGUserCounterDO counterDO = iapgUserCounterService.getOne(new QueryWrapper<APGUserCounterDO>().eq("user_id", usersDO.getId()));
        if (counterDO.getUserToken() >= userMaxLimit) {
            throw new ClientException(CHARACTER_LIMIT);
        }
        // 根据产品id获取相关数据，为生成做铺垫
        ProductDTO product = getProductsQueryByProductId(generateDescriptionVO.getProductId(), usersDO.getShopName(), usersDO.getAccessToken());
        GENERATE_SHOP_BAR.put(usersDO.getId(), product.getProductTitle());
        // 根据模板id获取模板数据
        TemplateDTO templateById = getTemplateById(generateDescriptionVO.getTemplateId(), usersDO.getId(), generateDescriptionVO.getTemplateType());
        // 根据 ProductDTO 和传入的 GenerateDescriptionVO进行描述生成(暂定qwen模型 图片理解)

        counter.addChars(counterDO.getUserToken());
        //生成提示词
        String prompt = buildDescriptionPrompt(product.getProductTitle(), product.getProductType(), product.getProductDescription(), generateDescriptionVO.getSeoKeywords(), product.getImageUrl(), product.getImageAltText(), generateDescriptionVO.getTextTone(), templateById.getTemplateType(), generateDescriptionVO.getBrandTone(), templateById.getTemplateData(), generateDescriptionVO.getLanguage());
        appInsights.trackTrace(usersDO.getShopName() + " 用户 " + product.getId() + " 的提示词为 ： " + prompt);
        //调用大模型翻译
        String des = aLiYunTranslateIntegration.callWithPicMess(prompt, usersDO.getId(), counter, product.getImageUrl(), userMaxLimit);
//        每次生成都要更新一下版本记录和生成数据
        iapgUserProductService.updateProductVersion(usersDO.getId(), generateDescriptionVO.getProductId(), des);
        return des;
    }

    /**
     * 根据产品id获取相关数据，为翻译做铺垫
     * */
    public ProductDTO getProductsQueryByProductId(String productId, String shopName, String accessToken) {
        String productDataQuery = getProductDataQuery(productId);
        String productData = getShopifyDataByEnv(shopName, accessToken, productDataQuery);
        ProductDTO productDTO = new ProductDTO();
        // 对productData进行解析，输出productDTO类型数据
        try {
            JsonNode root = OBJECT_MAPPER.readTree(productData);
            productDTO.setProductDescription(root.at("/product/description").asText(null));
            productDTO.setId(root.at("/product/id").asText());
            productDTO.setProductType(root.at("/product/productType").asText(null));
            productDTO.setImageUrl(root.at("/product/media/edges/0/node/image/url").asText(null));
            productDTO.setImageAltText(root.at("/product/media/edges/0/node/image/altText").asText(null));
            productDTO.setProductTitle(root.at("/product/title").asText(null));
            return productDTO;
        } catch (Exception e) {
            appInsights.trackTrace("getProductsQueryByProductId errors : " + e);
            appInsights.trackException(e);
            return null;
        }
    }

    /**
     *  包装下调用获取shopify数据的接口
     **/
    public String getShopifyDataByEnv(String shopName, String accessToken, String query) {
        String env = System.getenv("ApplicationEnv");
        String infoByShopify;
        ShopifyRequest request = new ShopifyRequest();
        request.setShopName(shopName);
        request.setAccessToken(accessToken);
        CloudServiceRequest cloudServiceRequest = new CloudServiceRequest();
        cloudServiceRequest.setShopName(shopName);
        cloudServiceRequest.setBody(query);
        cloudServiceRequest.setAccessToken(accessToken);
        if ("prod".equals(env) || "dev".equals(env)) {
            infoByShopify = String.valueOf(getInfoByShopify(request, query));
        } else {
            infoByShopify = getShopifyDataByCloud(cloudServiceRequest);
        }
        return infoByShopify;
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
}
