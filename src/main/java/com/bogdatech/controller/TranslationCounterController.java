package com.bogdatech.controller;

import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.entity.DO.TranslationCounterDO;
import com.bogdatech.entity.VO.TranslationCharsVO;
import com.bogdatech.logic.TranslationCounterService;
import com.bogdatech.model.controller.request.TranslationCounterRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import static com.bogdatech.enums.ErrorEnum.*;

@RestController
@RequestMapping("/translationCounter")
public class TranslationCounterController {

    private final ITranslationCounterService iTranslationCounterService;
    private final TranslationCounterService translationCounterService;
    @Autowired
    public TranslationCounterController(ITranslationCounterService iTranslationCounterService, TranslationCounterService translationCounterService) {
        this.iTranslationCounterService = iTranslationCounterService;
        this.translationCounterService = translationCounterService;
    }

    //给用户添加一个免费额度
    @PostMapping("/insertCharsByShopName")
    public BaseResponse<Object> insertCharsByShopName(@RequestBody TranslationCounterRequest request) {
        TranslationCounterDO translationCounterDO = iTranslationCounterService.readCharsByShopName(request.getShopName());
        if (translationCounterDO == null) {
            Integer result = iTranslationCounterService.insertCharsByShopName(request);
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
    public BaseResponse<Object> getCharsByShopName(@RequestParam String shopName) {
       TranslationCounterDO translatesDOS = iTranslationCounterService.readCharsByShopName(shopName);
        if (translatesDOS != null){
            return new BaseResponse().CreateSuccessResponse(translatesDOS);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_SELECT_ERROR);
    }

    @PutMapping("/updateCharsByShopName")
    public BaseResponse<Object> updateCharsByShopName(@RequestBody TranslationCounterRequest request) {
        int result = iTranslationCounterService.updateUsedCharsByShopName(request);
        if (result > 0) {
            return new BaseResponse().CreateSuccessResponse(result);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_UPDATE_ERROR);
    }

    //添加字符额度
    @PostMapping("/addCharsByShopName")
    public BaseResponse<Object> addCharsByShopName(@RequestBody TranslationCounterRequest request) {
        if (iTranslationCounterService.updateCharsByShopName(request)){
            return new BaseResponse<>().CreateSuccessResponse(SERVER_SUCCESS);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_UPDATE_ERROR);
    }

    /**
     * 订阅付费计划后，后端判断是否是免费计划，是的话，不添加额度；不是的话，添加额度
     * */
    @PostMapping("/addCharsByShopNameAfterSubscribe")
    public BaseResponse<Object> addCharsByShopNameAfterSubscribe(@RequestParam String shopName, @RequestBody TranslationCharsVO translationCharsVO) {
        Boolean flag = translationCounterService.addCharsByShopNameAfterSubscribe(shopName, translationCharsVO);
        if (flag == null) {
            return new BaseResponse<>().CreateErrorResponse(false);
        }
        if (flag) {
            return new BaseResponse<>().CreateSuccessResponse(true);
        }else {
            return new BaseResponse<>().CreateSuccessResponse(false);
        }
    }
}
