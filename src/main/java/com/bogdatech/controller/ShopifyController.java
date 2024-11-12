package com.bogdatech.controller;

import com.alibaba.fastjson.JSONObject;
import com.bogdatech.entity.TranslatesDO;
import com.bogdatech.enums.ErrorEnum;
import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.logic.ShopifyService;
import com.bogdatech.model.controller.request.*;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.repository.JdbcRepository;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class ShopifyController {
    @Autowired
    private ShopifyHttpIntegration shopifyApiIntegration;

    @Autowired
    private JdbcRepository jdbcRepository;

    @Autowired
    private ShopifyService shopifyService;

    private final TelemetryClient appInsights = new TelemetryClient();

    //通过测试环境调shopify的API
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

    //通过测试环境调shopify的API
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

    //根据前端的传值,更新shopify后台和数据库
    @PostMapping("/shopify/updateShopifyDataByTranslateTextRequest")
    public BaseResponse<Object> updateShopifyDataByTranslateTextRequest(@RequestBody RegisterTransactionRequest registerTransactionRequest) {
        return shopifyService.updateShopifyDataByTranslateTextRequest(registerTransactionRequest);
    }

    //获取用户的额度字符数 和 已使用的字符
    @PostMapping("/shopify/getUserLimitChars")
    public BaseResponse<Object> getUserLimitChars(@RequestBody TranslationCounterRequest request) {
        List<TranslationCounterRequest> translationCounterRequests = jdbcRepository.readCharsByShopName(request);
        Map<String, Object> map = new HashMap<>();
        map.put("chars", translationCounterRequests.get(0).getChars());
        map.put("totalChars", translationCounterRequests.get(0).getTotalChars());
        return new BaseResponse<>().CreateSuccessResponse(map);
    }

    //当用户第一次订阅时，在用户订阅表里面添加用户及其付费计划
    @PostMapping("/shopify/addUserFreeSubscription")
    public BaseResponse<Object> registerTransaction(@RequestBody UserSubscriptionsRequest request) {
       return shopifyService.addUserFreeSubscription(request);
    }

    //获取用户订阅计划
    @PostMapping("/shopify/getUserSubscriptionPlan")
    public BaseResponse<Object> getUserSubscriptionPlan(@RequestBody ShopifyRequest request) {
        String userSubscriptionPlan = jdbcRepository.getUserSubscriptionPlan(request);
        if (userSubscriptionPlan == null) {
            return new BaseResponse<>().CreateErrorResponse(ErrorEnum.SQL_SELECT_ERROR);
        }else {
            return new BaseResponse<>().CreateSuccessResponse(userSubscriptionPlan);
        }
    }

    //根据前端传来的值，返回对应的图片信息
    @PostMapping("/shopify/getImageInfo")
    public BaseResponse<Object> getImageInfo(@RequestBody String[] strings) {
        Map<String, String[]> imageInfo = shopifyService.getImageInfo(strings);
        return new BaseResponse<>().CreateSuccessResponse(imageInfo);
    }

    //计算被翻译项的总数和已翻译的个数
    @PostMapping("/shopify/getTranslationItemsInfo")
    public BaseResponse<Object> getTranslationItemsInfo(@RequestBody String[] strings) {

        return null;
    }
}
