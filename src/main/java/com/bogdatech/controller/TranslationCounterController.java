package com.bogdatech.controller;

import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.entity.TranslationCounterDO;
import com.bogdatech.model.controller.request.TranslationCounterRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static com.bogdatech.enums.ErrorEnum.*;

@RestController
public class TranslationCounterController {

    @Autowired
    private ITranslationCounterService translationCounterService;

    //
    @PostMapping("/translationCounter/insertCharsByShopName")
    public BaseResponse<Object> insertCharsByShopName(@RequestBody TranslationCounterRequest request) {
        Integer result = translationCounterService.insertCharsByShopName(request);
        //int result = 1;
        if (result > 0) {
            return new BaseResponse().CreateSuccessResponse(result);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_INSERT_ERROR);
    }

    @PostMapping("/translationCounter/getCharsByShopName")
    public BaseResponse<Object> getCharsByShopName(@RequestBody TranslationCounterRequest request) {
       TranslationCounterDO translatesDOS = translationCounterService.readCharsByShopName(request);
        if (translatesDOS != null){
            return new BaseResponse().CreateSuccessResponse(translatesDOS);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_SELECT_ERROR);
    }

    @PostMapping("/translationCounter/updateCharsByShopName")
    public BaseResponse<Object> updateCharsByShopName(@RequestBody TranslationCounterRequest request) {
        int result = translationCounterService.updateUsedCharsByShopName(request);
        if (result > 0) {
            return new BaseResponse().CreateSuccessResponse(result);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_UPDATE_ERROR);
    }

    //添加字符额度
    @PostMapping("/translateCounter/addCharsByShopName")
    public BaseResponse<Object> addCharsByShopName(@RequestBody TranslationCounterRequest request) {
        if (translationCounterService.updateCharsByShopName(request)){
            return new BaseResponse<>().CreateSuccessResponse(SERVER_SUCCESS);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_UPDATE_ERROR);
    }
}
