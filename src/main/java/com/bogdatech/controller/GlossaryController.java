package com.bogdatech.controller;

import com.bogdatech.Service.IGlossaryService;
import com.bogdatech.entity.GlossaryDO;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static com.bogdatech.enums.ErrorEnum.SQL_INSERT_ERROR;

@RestController
public class GlossaryController {

    @Autowired
    private IGlossaryService glossaryService;

    //插入glossary数据
    @PostMapping("/glossary/insertGlossaryInfo")
    public BaseResponse<Object> insertGlossaryInfo(@RequestBody GlossaryDO glossaryDO) {
        if (glossaryService.insertGlossaryInfo(glossaryDO)){
            return new BaseResponse<>().CreateSuccessResponse(200);
        }
      return new BaseResponse<>().CreateErrorResponse(SQL_INSERT_ERROR);
    }

    //根据id
}
