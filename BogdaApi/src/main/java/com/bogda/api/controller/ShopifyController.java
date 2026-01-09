package com.bogda.api.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bogda.service.Service.*;
import com.bogda.service.entity.DO.*;
import com.bogda.service.entity.VO.SubscriptionVO;
import com.bogda.service.integration.ShopifyHttpIntegration;
import com.bogda.service.logic.ShopifyService;
import com.bogda.service.controller.request.*;
import com.bogda.service.controller.response.BaseResponse;
import com.bogda.common.contants.TranslateConstants;
import com.bogda.common.utils.AppInsightsUtils;
import com.bogda.common.utils.ShopifyRequestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.bogda.common.enums.ErrorEnum.SQL_SELECT_ERROR;
import static com.bogda.service.utils.StringUtils.parsePlanName;

@RestController
@RequestMapping("/shopify")
public class ShopifyController {
    @Autowired
    private ShopifyService shopifyService;
    @Autowired
    private ITranslatesService translatesService;
    @Autowired
    private ITranslationCounterService translationCounterService;
    @Autowired
    private IUserSubscriptionsService userSubscriptionsService;
    @Autowired
    private ICharsOrdersService charsOrdersService;
    @Autowired
    private IUsersService usersService;
    @Autowired
    private IUserTrialsService iUserTrialsService;
    @Autowired
    private ISubscriptionPlansService iSubscriptionPlansService;
    @Autowired
    private ShopifyHttpIntegration shopifyHttpIntegration;

    //通过测试环境调shopify的API
    @PostMapping("/test123")
    public String test(@RequestBody CloudServiceRequest cloudServiceRequest) {
        String body = cloudServiceRequest.getBody();
        JSONObject infoByShopify = null;
        try {
            infoByShopify = shopifyHttpIntegration.getInfoByShopify(cloudServiceRequest.getShopName(), cloudServiceRequest.getAccessToken(), body);
        } catch (Exception e) {
            AppInsightsUtils.trackException(e);
            AppInsightsUtils.trackTrace("FatalException test123 " + cloudServiceRequest.getShopName() + " 无法获取shopify数据");
        }
        if (infoByShopify == null || infoByShopify.isEmpty()) {
            return null;
        }
        return infoByShopify.toString();
    }

    // 用户消耗的字符数
    @GetMapping("/getConsumedWords")
    public BaseResponse<Object> getConsumedWords(@RequestParam String shopName) {
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
                AppInsightsUtils.trackException(e);
                AppInsightsUtils.trackTrace("FatalException getConsumedWords Error while getConsumedWords for shop " + e.getMessage());
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
    public BaseResponse<Object> getUserStatus(@RequestParam String shopName, @RequestParam String source) {
        List<TranslatesDO> translatesDos = translatesService.readInfoByShopName(shopName, source);
        return new BaseResponse<>().CreateSuccessResponse(translatesDos);
    }

    //查询需要翻译的总字数
    @PostMapping("/getUnTranslatedToken")
    public BaseResponse<Object> getTotalWords(@RequestParam String shopName, @RequestParam String modelType, @RequestParam String source, @RequestBody ShopifyRequest shopifyRequest) {
        TranslateResourceDTO translateResourceDTO = new TranslateResourceDTO(modelType, TranslateConstants.MAX_LENGTH, shopifyRequest.getTarget(), "");
        shopifyRequest.setShopName(shopName);
        //获取该用户未翻译和已翻译的
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
                    AppInsightsUtils.trackTrace("getUserLimitChars " + shopName + " chars: " + translationCounterRequests.getUsedChars() + " totalChars: " + maxCharsByShopName);
                    return new BaseResponse<>().CreateSuccessResponse(map);
                }
            } catch (Exception e) {
                // 日志记录错误，便于后续排查
                AppInsightsUtils.trackTrace("FatalException getUserLimitChars Error while getUserLimitChars for shop " + e.getMessage());
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

    // 在UserSubscription表里面添加一个购买了免费订阅计划的用户
    @PostMapping("/addUserFreeSubscription")
    public BaseResponse<Object> registerTransaction(@RequestBody UserSubscriptionsRequest request) {
        return shopifyService.addUserFreeSubscription(request);
    }

    private static BaseResponse<Object> checkWhiteList(String shopName, SubscriptionVO subscriptionVO, Integer feeType){
        if ("5bf8b3.myshopify.com".equals(shopName) || "c5ba7c-7c.myshopify.com".equals(shopName) || "digitevil.myshopify.com".equals(shopName)) {
            subscriptionVO.setUserSubscriptionPlan(6);
            subscriptionVO.setCurrentPeriodEnd(null);
            subscriptionVO.setFeeType(feeType);
            subscriptionVO.setPlanType("Premium");
            return new BaseResponse<>().CreateSuccessResponse(subscriptionVO);
        }
        return null;
    }

    //获取用户订阅计划
    @GetMapping("/getUserSubscriptionPlan")
    public BaseResponse<Object> getUserSubscriptionPlan(@RequestParam String shopName) {
        //判断shopName的值是否有
        if (shopName == null || shopName.isEmpty()) {
            return new BaseResponse<>().CreateErrorResponse("shopName is null");
        }
        SubscriptionVO subscriptionVO = new SubscriptionVO();
        UserSubscriptionsDO userSubscriptionsDO = userSubscriptionsService.getOne(new LambdaQueryWrapper<UserSubscriptionsDO>().eq(UserSubscriptionsDO::getShopName, shopName));

        if (userSubscriptionsDO == null) {
            AppInsightsUtils.trackTrace("getUserSubscriptionPlan 用户获取的数据失败： " + shopName);
            return new BaseResponse<>().CreateErrorResponse("userSubscriptionsDO is null");
        }

        // 根据计划id 去查id名称
        String planType = iSubscriptionPlansService.getOne(new LambdaQueryWrapper<SubscriptionPlansDO>().eq(SubscriptionPlansDO::getPlanId, userSubscriptionsDO.getPlanId())).getPlanName();

        // 判断计划名称
        String parsePlanType = parsePlanName(planType);
        if (parsePlanType == null) {
            return new BaseResponse<>().CreateErrorResponse("parsePlanType is null");
        }
        subscriptionVO.setPlanType(parsePlanType);

        if (userSubscriptionsDO.getFeeType() == null) {
            userSubscriptionsDO.setFeeType(0);
        }
        Integer userSubscriptionPlan = userSubscriptionsDO.getPlanId();
        subscriptionVO.setUserSubscriptionPlan(userSubscriptionPlan);

        BaseResponse<Object> objectBaseResponse = checkWhiteList(shopName, subscriptionVO, userSubscriptionsDO.getFeeType());
        if (objectBaseResponse != null) {
            return objectBaseResponse;
        }

        //如果是userSubscriptionPlan是1和2，传null
        if (userSubscriptionPlan == 1 || userSubscriptionPlan == 2 || userSubscriptionPlan == 8) {
            subscriptionVO.setCurrentPeriodEnd(null);
            subscriptionVO.setFeeType(0);
            return new BaseResponse<>().CreateSuccessResponse(subscriptionVO);
        }

        //判断是否是免费计划
        if (userSubscriptionPlan == 7) {
            subscriptionVO.setUserSubscriptionPlan(7);
            //根据shopName获取订阅计划过期的时间
            UserTrialsDO userTrialsDO = iUserTrialsService.getOne(new QueryWrapper<UserTrialsDO>().eq("shop_name", shopName));
            subscriptionVO.setCurrentPeriodEnd(String.valueOf(userTrialsDO.getTrialEnd()));
            subscriptionVO.setFeeType(userSubscriptionsDO.getFeeType());
            return new BaseResponse<>().CreateSuccessResponse(subscriptionVO);
        }

        //根据shopName查询用户订阅计划，最新的那个，再根据最新的resourceId，查询是否过期
        CharsOrdersDO charsOrdersDO = charsOrdersService.list(new QueryWrapper<CharsOrdersDO>()
                        .eq("shop_name", shopName)
                        .eq("status", "ACTIVE")
                        .orderByDesc("updated_date"))
                .stream().filter(order -> order.getId() != null && order.getId().contains("AppSubscription"))
                .findFirst().orElse(null);

        if (charsOrdersDO == null) {
            return new BaseResponse<>().CreateErrorResponse("charsOrdersDO is null");
        }
        UsersDO usersDO = usersService.getOne(new QueryWrapper<UsersDO>()
                .eq("shop_name", shopName)
        );

        // 通过charsOrdersDO的id，获取信息
        // 根据新的集合获取这个订阅计划的信息
        String query = ShopifyRequestUtils.getSubscriptionQuery(charsOrdersDO.getId());
        String infoByShopify = shopifyService.getShopifyData(shopName, usersDO.getAccessToken(), TranslateConstants.API_VERSION_LAST, query);

        if (infoByShopify == null || infoByShopify.isEmpty()) {
            subscriptionVO.setFeeType(0);
            subscriptionVO.setUserSubscriptionPlan(2);
            subscriptionVO.setCurrentPeriodEnd(null);
            return new BaseResponse<>().CreateSuccessResponse(subscriptionVO);
        }

        // 根据订阅计划信息，判断是否是第一个月的开始
        JSONObject root = JSON.parseObject(infoByShopify);
        JSONObject node = root.getJSONObject("node");
        if (node == null || node.isEmpty()) {
            // 用户卸载，计划会被取消，但不确定其他情况
            subscriptionVO.setFeeType(0);
            subscriptionVO.setUserSubscriptionPlan(2);
            subscriptionVO.setCurrentPeriodEnd(null);
        } else {
            subscriptionVO.setFeeType(userSubscriptionsDO.getFeeType());
            String currentPeriodEnd = node.getString("currentPeriodEnd");
            subscriptionVO.setCurrentPeriodEnd(currentPeriodEnd);
        }
        return new BaseResponse<>().CreateSuccessResponse(subscriptionVO);
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

    //先从数据库中获取在调用getItemsByShopName
    @PostMapping("/getItemsInSqlByShopName")
    public BaseResponse<Object> getItemsInSqlByShopName(@RequestBody ResourceTypeRequest request) {
        return shopifyService.getItemsByShopName(request);
    }

    //修改多条文本
    @PostMapping("/updateItems")
    public BaseResponse<Object> updateItems(@RequestBody List<RegisterTransactionRequest> registerTransactionRequest) {
        if (registerTransactionRequest.isEmpty()) {
            return new BaseResponse<>().CreateErrorResponse("registerTransactionRequest is empty");
        }
        String s = shopifyService.updateShopifyDataByTranslateTextRequests(registerTransactionRequest);
        AppInsightsUtils.trackTrace("updateItems 用户 " + registerTransactionRequest.get(0).getShopName() + " 返回数据 response: " + s);
        if (s.contains("\"value\":")) {
            return new BaseResponse<>().CreateSuccessResponse(200);
        } else {
            return new BaseResponse<>().CreateErrorResponse(s);
        }
    }


}
