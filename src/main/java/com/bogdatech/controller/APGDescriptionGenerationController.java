package com.bogdatech.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogdatech.Service.IAPGUserPlanService;
import com.bogdatech.Service.IAPGUsersService;
import com.bogdatech.entity.DO.APGUsersDO;
import com.bogdatech.entity.VO.GenerateDescriptionVO;
import com.bogdatech.exception.ClientException;
import com.bogdatech.logic.GenerateDescriptionService;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.utils.CharacterCountUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
        try {
            description = generateDescriptionService.generateDescription(usersDO, generateDescriptionVO, new CharacterCountUtils(), userMaxLimit);
            appInsights.trackTrace(shopName + " generateDescription: " + description);
//            System.out.println(shopName + " generateDescription: " + description);
        } catch (ClientException e) {
            appInsights.trackTrace("shopName : " + shopName + " generateDescription errors : " + e.getMessage());
            return new BaseResponse<>().CreateErrorResponse(CHARACTER_LIMIT);
        }
        if (description != null){
            return new BaseResponse<>().CreateSuccessResponse(description);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }
}
