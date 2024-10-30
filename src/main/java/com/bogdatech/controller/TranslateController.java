package com.bogdatech.controller;

import com.alibaba.fastjson.JSONObject;
import com.bogdatech.entity.TranslatesDO;
import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.logic.TranslateService;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.query.ShopifyQuery;
import com.bogdatech.repository.JdbcRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.bogdatech.enums.ErrorEnum.SQL_SELECT_ERROR;

@RestController

public class TranslateController {

    @Autowired
    private TranslateService translateService;

    @Autowired
    private JdbcRepository jdbcRepository;
    @Autowired
    private ShopifyHttpIntegration shopifyApiIntegration;

    @PostMapping("/translate")
    public BaseResponse translate(@RequestBody TranslatesDO request) {
        return translateService.translate(request);
    }

    @PostMapping("/translate/insertShopTranslateInfo")
    public BaseResponse insertShopTranslateInfo(@RequestBody TranslateRequest request) {
        return jdbcRepository.insertShopTranslateInfo(request);
    }

    @PostMapping("/translate/googleTranslate")
    public BaseResponse googleTranslate(@RequestBody TranslateRequest request) {
        return translateService.googleTranslate(request);
    }

    @PostMapping("/translate/baiDuTranslate")
    public BaseResponse baiDuTranslate(@RequestBody TranslateRequest request) {
        return translateService.baiDuTranslate(request);
    }

    /*
    * 读取所有的翻译状态信息
    */
    @PostMapping("/translate/readTranslateInfo")
    public BaseResponse readTranslateInfo(@RequestBody TranslatesDO request) {
        List<TranslatesDO> list = jdbcRepository.readTranslateInfo(request.getStatus());
        if (list != null && list.size() > 0) {
            return new BaseResponse().CreateSuccessResponse(list);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_SELECT_ERROR);
    }

    /*
     * 读取shopName的所有翻译状态信息
     */
    @PostMapping("/translate/updateTranslateInfo")
    public BaseResponse readInfoByShopName(@RequestBody TranslateRequest request) {
        return jdbcRepository.readInfoByShopName(request);
    }

    @PostMapping("/translate/userBDTranslateJson")
    public BaseResponse userBDTranslateJsonObject() {
        return translateService.userBDTranslateJsonObject();
    }

    @PostMapping("/translate/readJsonFile")
    public BaseResponse userBDTranslateJson() {
        return new BaseResponse<>().CreateSuccessResponse( translateService.readJsonFile());
    }

    @PostMapping("/translate/translateJson")
    public BaseResponse translateJson(@RequestBody ShopifyRequest shopifyRequest) {
        return new BaseResponse<>().CreateSuccessResponse(translateService.translateJson(shopifyApiIntegration.getInfoByShopify(shopifyRequest, ShopifyQuery.PRODUCT2_QUERY)));
//        return new BaseResponse<>().CreateSuccessResponse((shopifyApiIntegration.getInfoByShopify(shopifyRequest, ShopifyQuery.PRODUCT2_QUERY)));
    }

    @PostMapping("/translate/translateString")
    public BaseResponse translate(@RequestBody JSONObject request) {
        return new BaseResponse<>().CreateSuccessResponse(translateService.translateJson(request));
    }

}
