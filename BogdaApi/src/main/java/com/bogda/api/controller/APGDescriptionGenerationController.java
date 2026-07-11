package com.bogda.api.controller;

import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.entity.VO.GenerateDescriptionVO;
import org.springframework.web.bind.annotation.*;

import static com.bogda.api.support.DisabledProductEndpoints.error;

@RestController
@RequestMapping("/apg/descriptionGeneration")
public class APGDescriptionGenerationController {

    @PostMapping("/generateDescription")
    public BaseResponse<Object> generateDescription(
            @RequestParam String shopName,
            @RequestBody GenerateDescriptionVO generateDescriptionVO) {
        return error();
    }
}
