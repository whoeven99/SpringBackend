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
import com.bogdatech.repository.JdbcRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.bogdatech.entity.TranslateResourceDTO.translationResources;
import static com.bogdatech.enums.ErrorEnum.SQL_INSERT_ERROR;
import static com.bogdatech.enums.ErrorEnum.SQL_SELECT_ERROR;

@RestController

public class TranslateController {

    @Autowired
    private TranslateService translateService;

    @Autowired
    private JdbcRepository jdbcRepository;
    @Autowired
    private ShopifyHttpIntegration shopifyApiIntegration;

    private TelemetryClient appInsights = new TelemetryClient();

    @PostMapping("/translate")
    public BaseResponse translate(@RequestBody TranslatesDO request) {
        return translateService.translate(request);
    }

    /*
     * 插入shop信息
     */
    @PostMapping("/translate/insertShopTranslateInfo")
    public BaseResponse insertShopTranslateInfo(@RequestBody TranslateRequest request) {
        int result = jdbcRepository.insertShopTranslateInfo(request);
        if (result > 0) {
            return new BaseResponse().CreateSuccessResponse(result);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_INSERT_ERROR);
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
        List<TranslatesDO> translatesDOS = jdbcRepository.readInfoByShopName(request);
        if (translatesDOS.size() > 0) {
            return new BaseResponse().CreateSuccessResponse(translatesDOS);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_SELECT_ERROR);
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
        return new BaseResponse<>().CreateSuccessResponse(translateService.readJsonFile());
    }

    /*
     *  传入json格式的数据，用百度翻译API翻译json格式的数据
     */
    @PostMapping("/translate/translateString")
    public BaseResponse translate(@RequestBody JSONObject request) {
        return new BaseResponse<>().CreateSuccessResponse(translateService.translateJson(request, new ShopifyRequest(), new TranslateResourceDTO()));
    }


    /*
     *  通过TranslateResourceDTO获取定义好的数组，对其进行for循环，遍历获得query，通过发送shopify的API获得数据，获得数据后再通过百度翻译API翻译数据
     */
    @GetMapping("testFor")
    public void test(@RequestBody ShopifyRequest shopifyRequest) {
        JSONObject objectData = new JSONObject();
        JsonNode jsonNode = null;
        for (TranslateResourceDTO translateResource : translationResources) {
            ShopifyQuery shopifyQuery = new ShopifyQuery();
            translateResource.setTarget(shopifyRequest.getTarget());
            String query = shopifyQuery.getFirstQuery(translateResource);
            appInsights.trackTrace(query);
            JSONObject infoByShopify = shopifyApiIntegration.getInfoByShopify(shopifyRequest, query);
            objectData.put(translateResource.getResourceType(), infoByShopify);
            jsonNode = translateService.translateJson(infoByShopify, shopifyRequest, translateResource);
        }
        appInsights.trackTrace("objectData: " + objectData);
        appInsights.trackTrace("jsonNode: " + jsonNode);
    }
}
