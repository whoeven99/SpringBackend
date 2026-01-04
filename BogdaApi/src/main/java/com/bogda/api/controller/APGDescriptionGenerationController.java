package com.bogda.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogda.api.Service.IAPGUserPlanService;
import com.bogda.api.Service.IAPGUsersService;
import com.bogda.api.entity.DO.APGUsersDO;
import com.bogda.api.entity.DTO.ProductDTO;
import com.bogda.api.entity.VO.APGAnalyzeDataVO;
import com.bogda.api.entity.VO.GenerateDescriptionVO;
import com.bogda.api.exception.ClientException;
import com.bogda.api.logic.GenerateDescriptionService;
import com.bogda.api.model.controller.response.BaseResponse;
import com.bogda.api.utils.CharacterCountUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import static com.bogda.common.constant.TranslateConstants.CHARACTER_LIMIT;
import static com.bogda.api.utils.CaseSensitiveUtils.appInsights;

@RestController
@RequestMapping("/apg/descriptionGeneration")
public class APGDescriptionGenerationController {
    @Autowired
    private GenerateDescriptionService generateDescriptionService;
    @Autowired
    private IAPGUsersService iapgUsersService;
    @Autowired
    private IAPGUserPlanService iapgUserPlanService;

    /**
     * 单条生成商品描述
     */
    @PostMapping("/generateDescription")
    public BaseResponse<Object> generateDescription(@RequestParam String shopName, @RequestBody GenerateDescriptionVO generateDescriptionVO) {
        // 根据shopName获取用户数据
        APGUsersDO usersDO = iapgUsersService.getOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getShopName, shopName));
        // 获取用户最大额度限制
        Integer userMaxLimit = iapgUserPlanService.getUserMaxLimit(usersDO.getId());

        // 实现生成描述的逻辑
        String description;
        ProductDTO product;
        try {
            product = generateDescriptionService.getProductsQueryByProductId(generateDescriptionVO.getProductId(), usersDO.getShopName(), usersDO.getAccessToken());
            description = generateDescriptionService.generateDescription(usersDO, generateDescriptionVO, new CharacterCountUtils(), userMaxLimit, product);
            appInsights.trackTrace("generateDescription" + shopName + " generateDescription: " + description);
            if (description == null) {
                return new BaseResponse<>().CreateErrorResponse(false);
            }
        } catch (ClientException e) {
            appInsights.trackTrace("generateDescription shopName : " + shopName + " generateDescription errors : " + e.getMessage());
            return new BaseResponse<>().CreateErrorResponse(CHARACTER_LIMIT);
        }
        //计算相关生成数据
        APGAnalyzeDataVO apgAnalyzeDataVO = generateDescriptionService.analyzeDescriptionData(description, product.getProductDescription(), generateDescriptionVO.getSeoKeywords());
        apgAnalyzeDataVO.setGenerateText(description);
        return new BaseResponse<>().CreateSuccessResponse(apgAnalyzeDataVO);
    }
}
