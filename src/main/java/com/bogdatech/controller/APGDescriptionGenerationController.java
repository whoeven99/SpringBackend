package com.bogdatech.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogdatech.Service.IAPGUserPlanService;
import com.bogdatech.Service.IAPGUsersService;
import com.bogdatech.entity.DO.APGUsersDO;
import com.bogdatech.entity.DTO.ProductDTO;
import com.bogdatech.entity.VO.APGAnalyzeDataVO;
import com.bogdatech.entity.VO.GenerateDescriptionVO;
import com.bogdatech.exception.ClientException;
import com.bogdatech.logic.GenerateDescriptionService;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.utils.CharacterCountUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

import static com.bogdatech.constants.TranslateConstants.CHARACTER_LIMIT;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@RestController
@RequestMapping("/apg/descriptionGeneration")
public class APGDescriptionGenerationController {

    private final GenerateDescriptionService generateDescriptionService;
    private final IAPGUsersService iapgUsersService;
    private final IAPGUserPlanService iapgUserPlanService;
    @Autowired
    public APGDescriptionGenerationController(GenerateDescriptionService generateDescriptionService, IAPGUsersService iapgUsersService, IAPGUserPlanService iapgUserPlanService) {
        this.generateDescriptionService = generateDescriptionService;
        this.iapgUsersService = iapgUsersService;
        this.iapgUserPlanService = iapgUserPlanService;
    }

    /**
     * 单条生成商品描述
     * */
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
            appInsights.trackTrace(shopName + " generateDescription: " + description);
        } catch (ClientException e) {
            appInsights.trackTrace("shopName : " + shopName + " generateDescription errors : " + e.getMessage());
//            System.out.println("shopName : " + shopName + " generateDescription errors : " + e.getMessage());
            return new BaseResponse<>().CreateErrorResponse(CHARACTER_LIMIT);
        }
        //计算相关生成数据
        APGAnalyzeDataVO apgAnalyzeDataVO = generateDescriptionService.analyzeDescriptionData(description, product.getProductDescription(), generateDescriptionVO.getSeoKeywords());
        apgAnalyzeDataVO.setGenerateText(description);
        if (description != null) {
            return new BaseResponse<>().CreateSuccessResponse(apgAnalyzeDataVO);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }
}
