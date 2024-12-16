package com.bogdatech.controller;

import com.bogdatech.Service.IGlossaryService;
import com.bogdatech.entity.GlossaryDO;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.bogdatech.enums.ErrorEnum.*;

@RestController
@RequestMapping("/glossary")
public class GlossaryController {

    @Autowired
    private IGlossaryService glossaryService;

    //插入glossary数据
    @PostMapping("/insertGlossaryInfo")
    public BaseResponse<Object> insertGlossaryInfo(@RequestBody GlossaryDO glossaryDO) {
        //判断 如果数据库中有5个就不再插入了
        GlossaryDO[] singleGlossaryByShopNameAndSource = glossaryService.getGlossaryByShopName(glossaryDO.getShopName());
        if (singleGlossaryByShopNameAndSource.length >= 5){
            return new BaseResponse<>().CreateErrorResponse("If there are 5, no more insertions will be made.");
        }
        //判断是否冲突（sourceText， rangeCode， caseSensitive）
        for (GlossaryDO glossary: singleGlossaryByShopNameAndSource) {
            if (glossary.getSourceText().equals(glossaryDO.getSourceText())) {
                if (glossary.getRangeCode().equals(glossaryDO.getRangeCode()) || glossary.getRangeCode().equals("ALL") || glossaryDO.getRangeCode().equals("ALL")) {
                    return new BaseResponse<>().CreateErrorResponse("The information entered conflicts with existing");
                }
            }
        }

        try {
            if (glossaryService.insertGlossaryInfo(glossaryDO)){
                return new BaseResponse<>().CreateSuccessResponse(glossaryService.getSingleGlossaryByShopNameAndSource(glossaryDO.getShopName(), glossaryDO.getSourceText()));
            }
        } catch (Exception e) {
            return new BaseResponse<>().CreateErrorResponse(SQL_INSERT_ERROR);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_INSERT_ERROR);
    }

    //根据id删除glossary数据
    @PostMapping("/deleteGlossaryById")
    public BaseResponse<Object> deleteGlossaryById(@RequestBody GlossaryDO glossaryDO) {
        if (glossaryService.deleteGlossaryById(glossaryDO)){
            return new BaseResponse<>().CreateSuccessResponse(glossaryDO);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_DELETE_ERROR);
    }

    //根据shopName获得glossary数据
    @PostMapping("/getGlossaryByShopName")
    public BaseResponse<Object> getGlossaryByShopName(@RequestBody GlossaryDO glossaryDO) {
        return new BaseResponse<>().CreateSuccessResponse(glossaryService.getGlossaryByShopName(glossaryDO.getShopName()));
    }

    //根据id修改targetText，status，rangeCode，caseSensitive数据
    @PostMapping("/updateTargetTextById")
    public BaseResponse<Object> updateTargetTextById(@RequestBody GlossaryDO glossaryDO) {
        GlossaryDO[] singleGlossaryByShopNameAndSource = glossaryService.getGlossaryByShopName(glossaryDO.getShopName());
        //判断是否冲突（sourceText， rangeCode， caseSensitive）
        for (GlossaryDO glossary: singleGlossaryByShopNameAndSource) {
            if (glossary.getSourceText().equals(glossaryDO.getSourceText())) {
                //找冲突
                if (glossary.getRangeCode().equals("ALL") || glossaryDO.getRangeCode().equals("ALL") ) {
                    return new BaseResponse<>().CreateErrorResponse("The information entered conflicts with existing");
                }

                if (glossary.getRangeCode().equals(glossaryDO.getRangeCode())){
                    break;
                }

            }
        }
        if (glossaryService.updateGlossaryInfoById(glossaryDO)){
            return new BaseResponse<>().CreateSuccessResponse(glossaryDO);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_UPDATE_ERROR);
    }


}
