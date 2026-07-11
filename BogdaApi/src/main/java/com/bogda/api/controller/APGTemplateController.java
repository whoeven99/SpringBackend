package com.bogda.api.controller;

import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.entity.DO.APGUserTemplateDO;
import com.bogda.common.entity.DO.APGUserTemplateMappingDO;
import com.bogda.common.entity.DTO.TemplateDTO;
import org.springframework.web.bind.annotation.*;

import static com.bogda.api.support.DisabledProductEndpoints.error;

@RestController
@RequestMapping("/apg/template")
public class APGTemplateController {

    @PostMapping("/getAllTemplateData")
    public BaseResponse<Object> getAllTemplateData(@RequestParam String shopName, @RequestBody TemplateDTO templateDTO) {
        return error();
    }

    @PostMapping("/getTemplateByShopName")
    public BaseResponse<Object> getTemplateByShopName(@RequestParam String shopName) {
        return error();
    }

    @PostMapping("/createUserTemplate")
    public BaseResponse<Object> createUserTemplate(@RequestParam String shopName, @RequestBody APGUserTemplateDO apgUserTemplateDO) {
        return error();
    }

    @PostMapping("/deleteUserTemplate")
    public BaseResponse<Object> deleteUserTemplate(@RequestParam String shopName, @RequestBody TemplateDTO templateDTO) {
        return error();
    }

    @PostMapping("/addOfficialOrUserTemplate")
    public BaseResponse<Object> addOfficialOrUserTemplate(
            @RequestParam String shopName,
            @RequestBody APGUserTemplateMappingDO apgUserTemplateMappingDO) {
        return error();
    }

    @GetMapping("/previewExampleDataByTemplateId")
    public BaseResponse<Object> previewExampleDataByTemplateId(@RequestParam String shopName, @RequestParam Long templateId) {
        return error();
    }
}
