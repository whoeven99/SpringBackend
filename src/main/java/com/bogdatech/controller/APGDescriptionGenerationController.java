package com.bogdatech.controller;

import com.bogdatech.entity.VO.GenerateDescriptionVO;
import com.bogdatech.entity.VO.GenerateVO;
import com.bogdatech.logic.GenerateDescriptionService;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@RestController
@RequestMapping("/apg/descriptionGeneration")
public class APGDescriptionGenerationController {

    private final GenerateDescriptionService generateDescriptionService;
    @Autowired
    public APGDescriptionGenerationController(GenerateDescriptionService generateDescriptionService) {
        this.generateDescriptionService = generateDescriptionService;
    }

    @PostMapping("/generateDescription")
    public BaseResponse<Object> generateDescription(@RequestParam String shopName, @RequestBody GenerateDescriptionVO generateDescriptionVO) {
        // 实现生成描述的逻辑
        appInsights.trackTrace(shopName + " generateDescriptionVO: " + generateDescriptionVO );
        String description = generateDescriptionService.generateDescription(shopName, generateDescriptionVO);
        appInsights.trackTrace(shopName + " generateDescription: " + description);
        if (description != null){
            return new BaseResponse<>().CreateSuccessResponse(new GenerateVO(generateDescriptionVO.getPageType(), generateDescriptionVO.getContentType(), description, generateDescriptionVO.getId()));
        }
        return new BaseResponse<>().CreateErrorResponse(new GenerateVO(generateDescriptionVO.getPageType(), generateDescriptionVO.getContentType(), null, generateDescriptionVO.getId()));
    }
}
