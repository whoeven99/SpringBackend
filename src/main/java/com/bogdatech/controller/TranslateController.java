package com.bogdatech.controller;

import com.alibaba.fastjson.JSONObject;
import com.bogdatech.entity.TranslateResourceDTO;
import com.bogdatech.entity.TranslatesDO;
import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.logic.TranslateService;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.query.ShopifyQuery;
import com.bogdatech.query.TestQuery;
import com.bogdatech.repository.JdbcRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.bogdatech.entity.TranslateResourceDTO.translationResources;
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

    /*
     * 插入shop信息
     */
    @PostMapping("/translate/insertShopTranslateInfo")
    public BaseResponse insertShopTranslateInfo(@RequestBody TranslateRequest request) {
        return jdbcRepository.insertShopTranslateInfo(request);
    }

    /*
     * 调用谷歌翻译的API接口
     */
    @PostMapping("/translate/googleTranslate")
    public BaseResponse googleTranslate(@RequestBody TranslateRequest request) {
        return translateService.googleTranslate(request);
    }

    /*
     * 调用百度翻译的API接口
     */
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

    /*
     * 用百度翻译API翻译json格式的数据
     */
    @PostMapping("/translate/userBDTranslateJson")
    public BaseResponse userBDTranslateJsonObject() {
        return translateService.userBDTranslateJsonObject();
    }

    /*
     *  读取produck的json文件，用百度翻译API翻译json格式的数据
     */
    @PostMapping("/translate/readJsonFile")
    public BaseResponse userBDTranslateJson() {
        return new BaseResponse<>().CreateSuccessResponse( translateService.readJsonFile());
    }

    /*
     *  传入json格式的数据，用百度翻译API翻译json格式的数据
     */
    @PostMapping("/translate/translateString")
    public BaseResponse translate(@RequestBody JSONObject request) {
        return new BaseResponse<>().CreateSuccessResponse(translateService.translateJson(request));
    }

    /*
     *  先调用shopify的API，获得数据后再通过百度翻译API翻译数据
     */
    @PostMapping("/translate/translateJson")
    public BaseResponse translateJson(@RequestBody ShopifyRequest shopifyRequest) {
        return new BaseResponse<>().CreateSuccessResponse(translateService.translateJson(shopifyApiIntegration.getInfoByShopify(shopifyRequest, ShopifyQuery.PRODUCT2_QUERY)));
    }

    @GetMapping("testFor")
    public void test() {
        JSONObject objectData = new JSONObject();
        for (TranslateResourceDTO translateResource : translationResources) {
            TestQuery testQuery = new TestQuery();
            String query = testQuery.getTestQuery(translateResource);
            System.out.println(query);
            JSONObject infoByShopify = shopifyApiIntegration.getInfoByShopify(new ShopifyRequest(), query);
            objectData.put(translateResource.getResourceType(), infoByShopify);
        }
        System.out.println(objectData);
    }
}
