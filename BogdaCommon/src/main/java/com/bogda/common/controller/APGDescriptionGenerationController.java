package com.bogda.common.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogda.common.Service.IAPGUserPlanService;
import com.bogda.common.Service.IAPGUsersService;
import com.bogda.common.entity.DO.APGUsersDO;
import com.bogda.common.entity.DTO.ProductDTO;
import com.bogda.common.entity.VO.APGAnalyzeDataVO;
import com.bogda.common.entity.VO.GenerateDescriptionVO;
import com.bogda.common.exception.ClientException;
import com.bogda.common.logic.GenerateDescriptionService;
import com.bogda.common.model.controller.response.BaseResponse;
import com.bogda.common.utils.CharacterCountUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import static com.bogda.common.constants.TranslateConstants.CHARACTER_LIMIT;
import static com.bogda.common.utils.CaseSensitiveUtils.appInsights;

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
