package com.bogdatech.controller;

import com.alibaba.fastjson.JSONObject;
import com.bogdatech.entity.TranslatesDO;
import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.integration.TestingEnvironmentIntegration;
import com.bogdatech.logic.ShopifyService;
import com.bogdatech.model.controller.request.CloudServiceRequest;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.model.controller.request.TranslateTextRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.query.ShopifyQuery;
import com.bogdatech.repository.JdbcRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

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
        JSONObject infoByShopify = shopifyApiIntegration.getInfoByShopify(request, body);
        return infoByShopify.toString();
    }

    @PostMapping("/shopifyApi")
    public String shopifyApi(@RequestBody CloudServiceRequest cloudServiceRequest) {
        ShopifyQuery query = new ShopifyQuery();
        cloudServiceRequest.setBody(query.test());
        // 使用 ObjectMapper 将对象转换为 JSON 字符串
        ObjectMapper objectMapper = new ObjectMapper();
        String string;
        try {
            String requestBody = objectMapper.writeValueAsString(cloudServiceRequest);
            string = testingEnvironmentIntegration.sendShopifyPost("test123", requestBody);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return string;
    }

    // 用户消耗的字符数
    @PostMapping("/shopify/getConsumedWords")
    public int getConsumedWords(@RequestBody ShopifyRequest shopifyRequest) {
//        jdbcRepository.readCharsByShopName()
        return 10;
    }

    //获取用户的状态
    @PostMapping("/shopify/getUserStatus")
    public BaseResponse getUserStatus(@RequestBody TranslateRequest request) {
        List<TranslatesDO> translatesDOS = jdbcRepository.readInfoByShopName(request);
        return new BaseResponse<>().CreateSuccessResponse(translatesDOS);
    }

    //查询需要翻译的总字数
    @PostMapping("/shopify/getTotalWords")
    public String getTotalWords(@RequestBody ShopifyRequest shopifyRequest) {
        getTotalWords(shopifyRequest);

        return "1000";
    }

    //根据前端的传值,更新shopify后台和数据库
    @PostMapping("/shopify/updateShopifyDataByTranslateTextRequest")
    public String updateShopifyDataByTranslateTextRequest(@RequestBody TranslateTextRequest translateTextRequest) {
        return "success";
    }
}
