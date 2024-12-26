package com.bogdatech.controller;

import com.bogdatech.Service.IGlossaryService;
import com.bogdatech.entity.GlossaryDO;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

import static com.bogdatech.enums.ErrorEnum.*;

@RestController
@RequestMapping("/glossary")
public class GlossaryController {

    private final IGlossaryService glossaryService;
    @Autowired
    public GlossaryController(IGlossaryService glossaryService) {
        this.glossaryService = glossaryService;
    }

    //插入glossary数据
    @PostMapping("/insertGlossaryInfo")
    public BaseResponse<Object> insertGlossaryInfo(@RequestBody GlossaryDO glossaryDO) {
        //判断 如果数据库中有5个就不再插入了
        GlossaryDO[] singleGlossaryByShopNameAndSource = glossaryService.getGlossaryByShopName(glossaryDO.getShopName());
        if (singleGlossaryByShopNameAndSource.length > 1) {
            return new BaseResponse<>().CreateErrorResponse("If there is 1, no more insertions will be made.");
        }
        //判断是否冲突（sourceText， rangeCode， caseSensitive）
        for (GlossaryDO glossary : singleGlossaryByShopNameAndSource) {
            if (glossary.getSourceText().equals(glossaryDO.getSourceText())) {
                if (glossary.getRangeCode().equals(glossaryDO.getRangeCode()) || glossary.getRangeCode().equals("ALL") || glossaryDO.getRangeCode().equals("ALL")) {
                    return new BaseResponse<>().CreateErrorResponse("The information entered conflicts with existing");
                }
            }
        }
        Boolean b = glossaryService.insertGlossaryInfo(glossaryDO);
        if (b) {
            GlossaryDO glossary = glossaryService.getSingleGlossaryByShopNameAndSource(glossaryDO.getShopName(), glossaryDO.getSourceText(), glossaryDO.getRangeCode());
            return new BaseResponse<>().CreateSuccessResponse(glossary);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_INSERT_ERROR);
    }

    //根据id删除glossary数据
    @DeleteMapping("/deleteGlossaryById")
    public BaseResponse<Object> deleteGlossaryById(@RequestBody GlossaryDO glossaryDO) {
        if (glossaryService.deleteGlossaryById(glossaryDO)) {
            return new BaseResponse<>().CreateSuccessResponse(glossaryDO);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_DELETE_ERROR);
    }

    //根据shopName获得glossary数据
    @GetMapping("/getGlossaryByShopName")
    public BaseResponse<Object> getGlossaryByShopName(String shopName) {
        return new BaseResponse<>().CreateSuccessResponse(glossaryService.getGlossaryByShopName(shopName));
    }

    //根据id修改targetText，status，rangeCode，caseSensitive数据
    @PostMapping("/updateTargetTextById")
    public BaseResponse<Object> updateTargetTextById(@RequestBody GlossaryDO glossaryDO) {
        GlossaryDO[] singleGlossaryByShopNameAndSource = glossaryService.getGlossaryByShopName(glossaryDO.getShopName());
        //判断是否冲突（sourceText， rangeCode， caseSensitive）
        for (GlossaryDO glossary : singleGlossaryByShopNameAndSource) {
            if (glossary.getSourceText().equals(glossaryDO.getSourceText()) && (!Objects.equals(glossary.getId(), glossaryDO.getId()))) {
                // 当 rangeCode 为 "ALL" 时，处理冲突
                if ("ALL".equals(glossaryDO.getRangeCode())) {
                    // 如果当前已经有具体的 rangeCode 存在，不能修改为 ALL
                    return new BaseResponse<>().CreateErrorResponse("The rangeCode 'ALL' cannot conflict with specific rangeCode.");
                } else if ("ALL".equals(glossary.getRangeCode())) {
                    // 当已有项的 rangeCode 为 "ALL"，不能再修改为具体的 rangeCode
                    return new BaseResponse<>().CreateErrorResponse("The rangeCode 'ALL' cannot conflict with a specific rangeCode.");
                } else {
                    // 如果 rangeCode 不为 ALL，且相同的 sourceText 和 rangeCode 存在，直接跳过
                    if (glossary.getRangeCode().equals(glossaryDO.getRangeCode())) {
                        return new BaseResponse<>().CreateErrorResponse("The rangeCode '" + glossaryDO.getRangeCode() + "' cannot conflict with the same rangeCode.");
//                    break;
                    }
                }
            }
        }
        if (glossaryService.updateGlossaryInfoById(glossaryDO)) {
            return new BaseResponse<>().CreateSuccessResponse(glossaryDO);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_UPDATE_ERROR);
    }


}
