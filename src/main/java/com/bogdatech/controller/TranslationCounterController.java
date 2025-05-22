package com.bogdatech.controller;

import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.entity.DO.TranslationCounterDO;
import com.bogdatech.model.controller.request.TranslationCounterRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import static com.bogdatech.enums.ErrorEnum.*;

@RestController
@RequestMapping("/translationCounter")
public class TranslationCounterController {

    private final ITranslationCounterService translationCounterService;
    @Autowired
    public TranslationCounterController(ITranslationCounterService translationCounterService) {
        this.translationCounterService = translationCounterService;
    }

    //给用户添加一个免费额度
    @PostMapping("/insertCharsByShopName")
    public BaseResponse<Object> insertCharsByShopName(@RequestBody TranslationCounterRequest request) {
        TranslationCounterDO translationCounterDO = translationCounterService.readCharsByShopName(request.getShopName());
        if (translationCounterDO == null) {
            Integer result = translationCounterService.insertCharsByShopName(request);
            //int result = 1;
            if (result > 0) {
                return new BaseResponse().CreateSuccessResponse(result);
            }else {
                return new BaseResponse<>().CreateSuccessResponse(null);
            }
        }else {
            return new BaseResponse<>().CreateSuccessResponse(null);
        }


    }

    @GetMapping("/getCharsByShopName")
    public BaseResponse<Object> getCharsByShopName(String shopName) {
       TranslationCounterDO translatesDOS = translationCounterService.readCharsByShopName(shopName);
        if (translatesDOS != null){
            return new BaseResponse().CreateSuccessResponse(translatesDOS);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_SELECT_ERROR);
    }

    @PutMapping("/updateCharsByShopName")
    public BaseResponse<Object> updateCharsByShopName(@RequestBody TranslationCounterRequest request) {
        int result = translationCounterService.updateUsedCharsByShopName(request);
        if (result > 0) {
            return new BaseResponse().CreateSuccessResponse(result);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_UPDATE_ERROR);
    }

    //添加字符额度
    @PostMapping("/addCharsByShopName")
    public BaseResponse<Object> addCharsByShopName(@RequestBody TranslationCounterRequest request) {
//        translationCounterService.updateCharsByShopName(request);
        if (translationCounterService.updateCharsByShopName(request)){
            return new BaseResponse<>().CreateSuccessResponse(SERVER_SUCCESS);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_UPDATE_ERROR);
    }
}
