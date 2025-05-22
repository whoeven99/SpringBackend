package com.bogdatech.controller;

import com.bogdatech.Service.ITranslateTextService;
import com.bogdatech.entity.DO.TranslateTextDO;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/translateText")
public class TranslateTextController {

    private final ITranslateTextService translateTextService;
    @Autowired
    public TranslateTextController(ITranslateTextService translateTextService) {
        this.translateTextService = translateTextService;
    }

    //修改数据库TranslateTextTable数据（用于dataResource翻译）
   @PostMapping("/updateOrInsertTranslateTextTable")
   public BaseResponse<Object> updateOrInsertTranslateTextTable(@RequestBody TranslateTextDO translateTextDO){
        Integer i = translateTextService.updateOrInsertTranslateTextTable(translateTextDO);
        return new BaseResponse<>().CreateSuccessResponse(i > 0 ? "success" : "fail");
   }
}
