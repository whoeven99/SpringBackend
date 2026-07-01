package com.bogda.api.controller;

import com.bogda.common.controller.request.*;
import com.bogda.common.entity.DO.*;
import com.bogda.common.reporter.ExceptionReporterHolder;
import com.bogda.common.reporter.TraceReporterHolder;
import com.bogda.service.Service.*;
import com.bogda.service.logic.ShopifyService;
import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.contants.TranslateConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.bogda.common.enums.ErrorEnum.SQL_SELECT_ERROR;

@RestController
@RequestMapping("/shopify")
public class ShopifyController {
    @Autowired
    private ShopifyService shopifyService;
    @Autowired
    private ITranslationCounterService translationCounterService;

    //查询需要翻译的总字数
    @PostMapping("/getUnTranslatedToken")
    public BaseResponse<Object> getTotalWords(@RequestParam String shopName, @RequestParam String modelType, @RequestParam String source, @RequestBody ShopifyRequest shopifyRequest) {
        TranslateResourceDTO translateResourceDTO = new TranslateResourceDTO(modelType, TranslateConstants.MAX_LENGTH, shopifyRequest.getTarget(), "");
        shopifyRequest.setShopName(shopName);
        int totalWords = shopifyService.getUnTranslatedToken(shopifyRequest, null, translateResourceDTO, source);
        return new BaseResponse<>().CreateSuccessResponse(totalWords);
    }

    //根据前端的传值,更新shopify后台和数据库
    @PostMapping("/updateShopifyDataByTranslateTextRequest")
    public BaseResponse<Object> updateShopifyDataByTranslateTextRequest(@RequestBody RegisterTransactionRequest registerTransactionRequest) {
        return shopifyService.updateShopifyDataByTranslateTextRequest(registerTransactionRequest);
    }

    //获取用户的额度字符数 和 已使用的字符
    @GetMapping("/getUserLimitChars")
    public BaseResponse<Object> getUserLimitChars(@RequestParam String shopName) {
        TranslationCounterDO translationCounterRequests;
        int retryCount = 3;
        int retryDelay = 1000;
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < retryCount; i++) {
            try {
                translationCounterRequests = translationCounterService.readCharsByShopName(shopName);
                if (translationCounterRequests != null) {
                    Integer maxCharsByShopName = translationCounterService.getMaxCharsByShopName(shopName);
                    map.put("chars", translationCounterRequests.getUsedChars());
                    map.put("totalChars", maxCharsByShopName);
                    TraceReporterHolder.report("ShopifyController.getUserLimitChars", "getUserLimitChars " + shopName + " chars: " + translationCounterRequests.getUsedChars() + " totalChars: " + maxCharsByShopName);
                    return new BaseResponse<>().CreateSuccessResponse(map);
                }
            } catch (Exception e) {
                TraceReporterHolder.report("ShopifyController.getUserLimitChars", "FatalException getUserLimitChars Error while getUserLimitChars for shop " + shopName);
                ExceptionReporterHolder.report("ShopifyController.getUserLimitChars", e);
            }

            try {
                Thread.sleep(retryDelay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            retryDelay *= 2;
        }

        return new BaseResponse<>().CreateErrorResponse(SQL_SELECT_ERROR);
    }

    // 在UserSubscription表里面添加一个购买了免费订阅计划的用户
    @PostMapping("/addUserFreeSubscription")
    public BaseResponse<Object> registerTransaction(@RequestBody UserSubscriptionsRequest request) {
        return shopifyService.addUserFreeSubscription(request);
    }

    //获取用户订阅计划
    @GetMapping("/getUserSubscriptionPlan")
    public BaseResponse<Object> getUserSubscriptionPlan(@RequestParam String shopName) {
        if (shopName == null || shopName.isEmpty()) {
            return new BaseResponse<>().CreateErrorResponse("shopName is null");
        }

        return shopifyService.getUserSubscriptionPlan(shopName);
    }

    //修改多条文本
    @PostMapping("/updateItems")
    public BaseResponse<Object> updateItems(@RequestBody List<RegisterTransactionRequest> registerTransactionRequest) {
        if (registerTransactionRequest.isEmpty()) {
            return new BaseResponse<>().CreateErrorResponse("registerTransactionRequest is empty");
        }
        String s = shopifyService.updateShopifyDataByTranslateTextRequests(registerTransactionRequest);
        TraceReporterHolder.report("ShopifyController.updateItems", "updateItems 用户 " + registerTransactionRequest.get(0).getShopName() + " 返回数据 response: " + s);
        if (s.contains("\"value\":")) {
            return new BaseResponse<>().CreateSuccessResponse(200);
        } else {
            return new BaseResponse<>().CreateErrorResponse(s);
        }
    }
}
