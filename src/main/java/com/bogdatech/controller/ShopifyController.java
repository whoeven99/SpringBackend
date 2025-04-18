package com.bogdatech.controller;

import com.alibaba.fastjson.JSONObject;
import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.Service.IUserSubscriptionsService;
import com.bogdatech.entity.TranslateResourceDTO;
import com.bogdatech.entity.TranslatesDO;
import com.bogdatech.entity.TranslationCounterDO;
import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.logic.ShopifyService;
import com.bogdatech.model.controller.request.*;
import com.bogdatech.model.controller.response.BaseResponse;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.bogdatech.entity.TranslateResourceDTO.RESOURCE_MAP;
import static com.bogdatech.entity.TranslateResourceDTO.TOKEN_MAP;
import static com.bogdatech.enums.ErrorEnum.SQL_SELECT_ERROR;
import static com.bogdatech.enums.ErrorEnum.SQL_UPDATE_ERROR;

@RestController
@RequestMapping("/shopify")
public class ShopifyController {

    private final ShopifyHttpIntegration shopifyApiIntegration;
    private final ShopifyService shopifyService;
    private final ITranslatesService translatesService;
    private final ITranslationCounterService translationCounterService;
    private final IUserSubscriptionsService userSubscriptionsService;

    @Autowired
    public ShopifyController(ShopifyHttpIntegration shopifyApiIntegration, ShopifyService shopifyService, ITranslatesService translatesService, ITranslationCounterService translationCounterService, IUserSubscriptionsService userSubscriptionsService) {
        this.shopifyApiIntegration = shopifyApiIntegration;
        this.shopifyService = shopifyService;
        this.translatesService = translatesService;
        this.translationCounterService = translationCounterService;
        this.userSubscriptionsService = userSubscriptionsService;

    }

    private final TelemetryClient appInsights = new TelemetryClient();

    //通过测试环境调shopify的API
    @PostMapping("/test123")
    public String test(@RequestBody CloudServiceRequest cloudServiceRequest) {
        ShopifyRequest request = new ShopifyRequest();
        request.setShopName(cloudServiceRequest.getShopName());
        request.setAccessToken(cloudServiceRequest.getAccessToken());
        request.setTarget(cloudServiceRequest.getTarget());
        String body = cloudServiceRequest.getBody();
        JSONObject infoByShopify = null;
        try {
            infoByShopify = shopifyApiIntegration.getInfoByShopify(request, body);
        } catch (Exception e) {
//            throw new RuntimeException(e);
            appInsights.trackTrace("无法获取shopify数据");
        }
        if (infoByShopify == null) {
            return null;
        }
        return infoByShopify.toString();
    }

    //通过测试环境调shopify的API
    @PostMapping("/shopifyApi")
    public BaseResponse<Object> shopifyApi(@RequestBody CloudServiceRequest cloudServiceRequest) {
        return new BaseResponse<>().CreateSuccessResponse(shopifyService.getShopifyData(cloudServiceRequest));
    }

    // 用户消耗的字符数
    @GetMapping("/getConsumedWords")
    public BaseResponse<Object> getConsumedWords(String shopName) {
        TranslationCounterDO translationCounterRequests;
        int retryCount = 3; // 最大重试次数
        int retryDelay = 1000; // 重试间隔时间，单位毫秒

        for (int i = 0; i < retryCount; i++) {
            try {
                translationCounterRequests = translationCounterService.readCharsByShopName(shopName);
                if (translationCounterRequests != null) {
                    return new BaseResponse<>().CreateSuccessResponse(translationCounterRequests.getUsedChars());
                }
            } catch (Exception e) {
                // 日志记录错误，便于后续排查
                appInsights.trackTrace("Error while getConsumedWords for shop " + e.getMessage());
            }

            // 如果未成功且重试次数未达上限，等待一段时间后再重试
            try {
                Thread.sleep(retryDelay); // 重试间隔
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            retryDelay *= 2; // 可选：指数回退策略
        }

        return new BaseResponse<>().CreateErrorResponse(SQL_SELECT_ERROR);
    }

    //获取用户的状态
    @GetMapping("/getUserStatus")
    public BaseResponse<Object> getUserStatus(String shopName, String source) {
        List<TranslatesDO> translatesDos = translatesService.readInfoByShopName(shopName, source);
        return new BaseResponse<>().CreateSuccessResponse(translatesDos);
    }

    //查询需要翻译的总字数-已翻译字符数. 计算翻译的项数
    @PostMapping("/getTotalWords")
    public BaseResponse<Object> getTotalWords(@RequestBody ShopifyRequest shopifyRequest, String method) {
//        TranslateResourceDTO resourceType = new TranslateResourceDTO("PRODUCT","250","","");
        for (String key : TOKEN_MAP.keySet()
        ) {
            List<TranslateResourceDTO> lists = TOKEN_MAP.get(key);
            int tokens = 0;
            for (TranslateResourceDTO resourceDTO : lists
            ) {
                int totalWords = shopifyService.getTotalWords(shopifyRequest, method, resourceDTO);
                tokens += totalWords;
            }
        }
        return new BaseResponse<>().CreateSuccessResponse("success");
    }

    //根据前端的传值,更新shopify后台和数据库
    @PostMapping("/updateShopifyDataByTranslateTextRequest")
    public BaseResponse<Object> updateShopifyDataByTranslateTextRequest(@RequestBody RegisterTransactionRequest registerTransactionRequest) {
        return shopifyService.updateShopifyDataByTranslateTextRequest(registerTransactionRequest);
    }

    //获取用户的额度字符数 和 已使用的字符
    @GetMapping("/getUserLimitChars")
    public BaseResponse<Object> getUserLimitChars(String shopName) {
        TranslationCounterDO translationCounterRequests;
        int retryCount = 3; // 最大重试次数
        int retryDelay = 1000; // 重试间隔时间，单位毫秒
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < retryCount; i++) {
            try {
                translationCounterRequests = translationCounterService.readCharsByShopName(shopName);
                if (translationCounterRequests != null) {
                    Integer maxCharsByShopName = translationCounterService.getMaxCharsByShopName(shopName);
                    map.put("chars", translationCounterRequests.getUsedChars());
                    map.put("totalChars", maxCharsByShopName);
                    return new BaseResponse<>().CreateSuccessResponse(map);
                }
            } catch (Exception e) {
                // 日志记录错误，便于后续排查
                appInsights.trackTrace("Error while getUserLimitChars for shop " + e.getMessage());
            }

            // 如果未成功且重试次数未达上限，等待一段时间后再重试
            try {
                Thread.sleep(retryDelay); // 重试间隔
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            retryDelay *= 2; // 可选：指数回退策略
        }

        return new BaseResponse<>().CreateErrorResponse(SQL_SELECT_ERROR);
    }

    //当用户第一次订阅时，在用户订阅表里面添加用户及其付费计划
    @PostMapping("/addUserFreeSubscription")
    public BaseResponse<Object> registerTransaction(@RequestBody UserSubscriptionsRequest request) {
        return shopifyService.addUserFreeSubscription(request);
    }

    //获取用户订阅计划
    @GetMapping("/getUserSubscriptionPlan")
    public BaseResponse<Object> getUserSubscriptionPlan(String shopName) {
        Integer userSubscriptionPlan = userSubscriptionsService.getUserSubscriptionPlan(shopName);
        if (userSubscriptionPlan == null) {
            return new BaseResponse<>().CreateErrorResponse(SQL_SELECT_ERROR);
        } else {
            return new BaseResponse<>().CreateSuccessResponse(userSubscriptionPlan);
        }
    }

    //根据前端传来的值，返回对应的图片信息
    @PostMapping("/getImageInfo")
    public BaseResponse<Object> getImageInfo(@RequestBody String[] strings) {
        Map<String, Object> imageInfo = shopifyService.getImageInfo(strings);
        return new BaseResponse<>().CreateSuccessResponse(imageInfo);
    }

    //计算被翻译项的总数和已翻译的个数
    @PostMapping("/getTranslationItemsInfo")
    public BaseResponse<Object> getTranslationItemsInfo(@RequestBody ResourceTypeRequest request) {
        Map<String, Map<String, Object>> translationItemsInfo;
        translationItemsInfo = shopifyService.getTranslationItemsInfo(request);
        if (translationItemsInfo == null) {
            return new BaseResponse<>().CreateErrorResponse("Get items failed");
        } else {
            return new BaseResponse<>().CreateSuccessResponse(translationItemsInfo);
        }
    }

    //修改翻译状态
    @PutMapping("/updateTranslationStatus")
    public BaseResponse<Object> updateTranslationStatus(@RequestBody TranslateRequest request) {
        int i = shopifyService.updateTranslationStatus(request);
        if (i > 0) {
            return new BaseResponse<>().CreateSuccessResponse(200);
        } else {
            return new BaseResponse<>().CreateErrorResponse(SQL_UPDATE_ERROR);
        }
    }

    //先从数据库中获取在调用getItemsByShopName
    @PostMapping("/getItemsInSqlByShopName")
    public BaseResponse<Object> getItemsInSqlByShopName(@RequestBody ResourceTypeRequest request) {
        return shopifyService.getItemsByShopName(request);
    }

    //修改单行文本
    @PostMapping("/updateItem")
    public String updateItem(@RequestBody RegisterTransactionRequest registerTransactionRequest) {
        return shopifyService.updateShopifySingleData(registerTransactionRequest);
    }

    //修改多条文本
    @PostMapping("/updateItems")
    public BaseResponse<Object> updateItems(@RequestBody List<RegisterTransactionRequest> registerTransactionRequest) {
        String s = shopifyService.updateShopifyDataByTranslateTextRequests(registerTransactionRequest);
        if (s.contains("value")) {
            return new BaseResponse<>().CreateSuccessResponse(200);
        } else {
            return new BaseResponse<>().CreateErrorResponse(s);
        }
    }

    @PostMapping("/getTranslationItemsInfoTest")
    public void getTranslationItemsInfoTest(@RequestBody ResourceTypeRequest request) {
        for (String key : RESOURCE_MAP.keySet()
        ) {
            ResourceTypeRequest resourceTypeRequest = new ResourceTypeRequest();
            resourceTypeRequest.setResourceType(key);
            resourceTypeRequest.setTarget(request.getTarget());
            resourceTypeRequest.setAccessToken(request.getAccessToken());
            resourceTypeRequest.setShopName(request.getShopName());
            shopifyService.getTranslationItemsInfoTest(resourceTypeRequest);
        }
    }


}
