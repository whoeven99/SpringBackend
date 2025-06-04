package com.bogdatech.controller;

import com.bogdatech.entity.VO.GenerateDescriptionVO;
import com.bogdatech.entity.VO.GenerateVO;
import com.bogdatech.logic.GenerateDescriptionService;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/apg/descriptionGeneration")
public class APGDescriptionGenerationController {

    private final GenerateDescriptionService generateDescriptionService;
    @Autowired
    public APGDescriptionGenerationController(GenerateDescriptionService generateDescriptionService) {
        this.generateDescriptionService = generateDescriptionService;
    }

    @PostMapping("/generateDescription")
    public BaseResponse<Object> generateDescription(String shopName, @RequestBody GenerateDescriptionVO generateDescriptionVO) {
        // TODO: 实现生成描述的逻辑
        String description = generateDescriptionService.generateDescription(shopName, generateDescriptionVO);
        if (description != null){
            return new BaseResponse<>().CreateSuccessResponse(new GenerateVO(generateDescriptionVO.getPageType(), generateDescriptionVO.getContentType(), description));
        }
        return new BaseResponse<>().CreateErrorResponse(new GenerateVO(generateDescriptionVO.getPageType(), generateDescriptionVO.getContentType(), null));
    }
}
