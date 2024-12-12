package com.bogdatech.controller;

import com.bogdatech.Service.IGlossaryService;
import com.bogdatech.entity.GlossaryDO;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static com.bogdatech.enums.ErrorEnum.*;

@RestController
public class GlossaryController {

    @Autowired
    private IGlossaryService glossaryService;

    //插入glossary数据
    @PostMapping("/glossary/insertGlossaryInfo")
    public BaseResponse<Object> insertGlossaryInfo(@RequestBody GlossaryDO glossaryDO) {
        if (glossaryService.insertGlossaryInfo(glossaryDO)){
            return new BaseResponse<>().CreateSuccessResponse(glossaryService.getSingleGlossaryByShopNameAndSource(glossaryDO.getShopName(), glossaryDO.getSourceText()));
        }
      return new BaseResponse<>().CreateErrorResponse(SQL_INSERT_ERROR);
    }

    //根据id删除glossary数据
    @PostMapping("/glossary/deleteGlossaryById")
    public BaseResponse<Object> deleteGlossaryById(@RequestBody GlossaryDO glossaryDO) {
        if (glossaryService.deleteGlossaryById(glossaryDO)){
            return new BaseResponse<>().CreateSuccessResponse(200);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_DELETE_ERROR);
    }

    //根据shopName获得glossary数据
    @PostMapping("/glossary/getGlossaryByShopName")
    public BaseResponse<Object> getGlossaryByShopName(@RequestBody GlossaryDO glossaryDO) {
        return new BaseResponse<>().CreateSuccessResponse(glossaryService.getGlossaryByShopName(glossaryDO.getShopName()));
    }

    //根据id修改targetText，status，rangeCode，caseSensitive数据
    @PostMapping("/glossary/updateTargetTextById")
    public BaseResponse<Object> updateTargetTextById(@RequestBody GlossaryDO glossaryDO) {
        if (glossaryService.updateGlossaryInfoById(glossaryDO)){
            return new BaseResponse<>().CreateSuccessResponse(glossaryDO);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_UPDATE_ERROR);
    }


}
