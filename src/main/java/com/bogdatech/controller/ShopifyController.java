package com.bogdatech.controller;

import com.alibaba.fastjson.JSONObject;
import com.bogdatech.entity.TranslatesDO;
import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.integration.TestingEnvironmentIntegration;
import com.bogdatech.logic.ShopifyService;
import com.bogdatech.model.controller.request.*;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.repository.JdbcRepository;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
public class ShopifyController {
    @Autowired
    private ShopifyHttpIntegration shopifyApiIntegration;

    @Autowired
    private JdbcRepository jdbcRepository;

    @Autowired
    private ShopifyService shopifyService;

    @Autowired
    private TestingEnvironmentIntegration testingEnvironmentIntegration;

    private final TelemetryClient appInsights = new TelemetryClient();

    @PostMapping("/test123")
    public String test(@RequestBody CloudServiceRequest cloudServiceRequest) {
        ShopifyRequest request = new ShopifyRequest();
        request.setShopName(cloudServiceRequest.getShopName());
        request.setAccessToken(cloudServiceRequest.getAccessToken());
        request.setTarget(cloudServiceRequest.getTarget());
        String body = cloudServiceRequest.getBody();
        appInsights.trackTrace("body: " + body);
        appInsights.trackTrace("request: " + request);
        JSONObject infoByShopify = shopifyApiIntegration.getInfoByShopify(request, body);
        return infoByShopify.toString();
    }

    @PostMapping("/shopifyApi")
    public BaseResponse<Object> shopifyApi(@RequestBody CloudServiceRequest cloudServiceRequest) {
        return new BaseResponse<>().CreateSuccessResponse(shopifyService.getShopifyData(cloudServiceRequest));
    }

    // 用户消耗的字符数
    @PostMapping("/shopify/getConsumedWords")
    public BaseResponse<Object> getConsumedWords(@RequestBody TranslationCounterRequest request) {
        List<TranslationCounterRequest> translationCounterRequests = jdbcRepository.readCharsByShopName(request);
        return new BaseResponse<>().CreateSuccessResponse(translationCounterRequests.get(0).getUsedChars());
    }

    //获取用户的状态
    @PostMapping("/shopify/getUserStatus")
    public BaseResponse<Object> getUserStatus(@RequestBody TranslateRequest request) {
        List<TranslatesDO> translatesDos = jdbcRepository.readInfoByShopName(request);
        return new BaseResponse<>().CreateSuccessResponse(translatesDos.get(0).getStatus());
    }

    //查询需要翻译的总字数
    @PostMapping("/shopify/getTotalWords")
    public BaseResponse<Object> getTotalWords(@RequestBody ShopifyRequest shopifyRequest) {
        return new BaseResponse<>().CreateSuccessResponse(shopifyService.getTotalWords(shopifyRequest));
    }

    //根据前端的传值,更新shopify后台和数据库 TODO :需要优化一下
    @PostMapping("/shopify/updateShopifyDataByTranslateTextRequest")
    public String updateShopifyDataByTranslateTextRequest(@RequestBody RegisterTransactionRequest registerTransactionRequest) {
        String string = shopifyService.updateShopifySingleData(registerTransactionRequest);
        TranslateTextRequest request = new TranslateTextRequest();
        request.setShopName(registerTransactionRequest.getShopName());
        request.setTargetText(registerTransactionRequest.getValue());
        request.setDigest(registerTransactionRequest.getTranslatableContentDigest());
        request.setTargetCode(registerTransactionRequest.getLocale());
        jdbcRepository.updateTranslateText(request);
        return string;
    }

    //获取用户的额度字符数
    @PostMapping("/shopify/getUserLimitChars")
    public BaseResponse<Object> getUserLimitChars(@RequestBody TranslationCounterRequest request) {
        List<TranslationCounterRequest> translationCounterRequests = jdbcRepository.readCharsByShopName(request);
        return new BaseResponse<>().CreateSuccessResponse(translationCounterRequests.get(0).getChars());
    }

    //当用户第一次订阅时，在用户订阅表里面添加用户及其付费计划
    @PostMapping("/shopify/addUserSubscription")
    public BaseResponse<Object> registerTransaction(@RequestBody UserSubscriptionsRequest request) {
        request.setStatus(1);
        LocalDate localDate = LocalDate.now();
        request.setStartDate(localDate);
//        jdbcRepository.addUserSubscription(request);
        return null;
    }
}
