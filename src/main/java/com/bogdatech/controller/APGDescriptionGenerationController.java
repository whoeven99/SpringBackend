package com.bogdatech.controller;

import com.bogdatech.Service.IAPGUserPlanService;
import com.bogdatech.Service.IAPGUsersService;
import com.bogdatech.entity.DO.APGUsersDO;
import com.bogdatech.entity.DTO.ProductDTO;
import com.bogdatech.entity.VO.APGAnalyzeDataVO;
import com.bogdatech.entity.VO.GenerateDescriptionVO;
import com.bogdatech.exception.ClientException;
import com.bogdatech.logic.GenerateDescriptionService;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.task.GenerateDbTask;
import com.bogdatech.utils.CharacterCountUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import static com.bogdatech.constants.TranslateConstants.CHARACTER_LIMIT;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

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
        APGUsersDO usersDO = iapgUsersService.getUserByShopName(shopName);
        // 获取用户最大额度限制
        Integer userMaxLimit = iapgUserPlanService.getUserMaxLimit(usersDO.getId());

        // 实现生成描述的逻辑
        String description;
        ProductDTO product;
        try {
            product = generateDescriptionService.getProductsQueryByProductId(generateDescriptionVO.getProductId(), usersDO.getShopName(), usersDO.getAccessToken());
            description = generateDescriptionService.generateDescription(usersDO, generateDescriptionVO, new CharacterCountUtils(), userMaxLimit, product, GenerateDbTask.APG_SINGLE_TRANSLATE);
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
