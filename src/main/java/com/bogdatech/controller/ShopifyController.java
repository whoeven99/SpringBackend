package com.bogdatech.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bogdatech.Service.*;
import com.bogdatech.entity.DO.*;
import com.bogdatech.entity.VO.SubscriptionVO;
import com.bogdatech.logic.ShopifyService;
import com.bogdatech.model.controller.request.*;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.bogdatech.constants.TranslateConstants.API_VERSION_LAST;
import static com.bogdatech.entity.DO.TranslateResourceDTO.RESOURCE_MAP;
import static com.bogdatech.entity.DO.TranslateResourceDTO.TOKEN_MAP;
import static com.bogdatech.enums.ErrorEnum.SQL_SELECT_ERROR;
import static com.bogdatech.enums.ErrorEnum.SQL_UPDATE_ERROR;
import static com.bogdatech.integration.ShopifyHttpIntegration.getInfoByShopify;
import static com.bogdatech.logic.ShopifyService.getShopifyDataByCloud;
import static com.bogdatech.requestBody.ShopifyRequestBody.getSubscriptionQuery;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@RestController
@RequestMapping("/shopify")
public class ShopifyController {

    private final ShopifyService shopifyService;
    private final ITranslatesService translatesService;
    private final ITranslationCounterService translationCounterService;
    private final IUserSubscriptionsService userSubscriptionsService;
    private final ICharsOrdersService charsOrdersService;
    private final IUsersService usersService;
    private final IUserTrialsService iUserTrialsService;

    @Autowired
    public ShopifyController(ShopifyService shopifyService, ITranslatesService translatesService, ITranslationCounterService translationCounterService, IUserSubscriptionsService userSubscriptionsService, ICharsOrdersService charsOrdersService, IUsersService usersService, IUserTrialsService iUserTrialsService) {
        this.shopifyService = shopifyService;
        this.translatesService = translatesService;
        this.translationCounterService = translationCounterService;
        this.userSubscriptionsService = userSubscriptionsService;
        this.charsOrdersService = charsOrdersService;
        this.usersService = usersService;
        this.iUserTrialsService = iUserTrialsService;
    }

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
            infoByShopify = getInfoByShopify(request, body);
        } catch (Exception e) {
            appInsights.trackException(e);
            appInsights.trackTrace("test123 " + request.getShopName() + " 无法获取shopify数据");
        }
        if (infoByShopify == null || infoByShopify.isEmpty()) {
            return null;
        }
        return infoByShopify.toString();
    }

    //通过测试环境调shopify的API
    @PostMapping("/shopifyApi")
    public BaseResponse<Object> shopifyApi(@RequestBody CloudServiceRequest cloudServiceRequest) {
        return new BaseResponse<>().CreateSuccessResponse(getShopifyDataByCloud(cloudServiceRequest));
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
                appInsights.trackException(e);
                appInsights.trackTrace("getConsumedWords Error while getConsumedWords for shop " + e.getMessage());
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

    //查询需要翻译的总字数-已翻译字符数. 计算翻译的项数
    @PostMapping("/getTotalWords")
    public BaseResponse<Object> getTotalWords(@RequestBody ShopifyRequest shopifyRequest, @RequestParam String method) {
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
                    appInsights.trackTrace("getUserLimitChars " + shopName + " chars: " + translationCounterRequests.getUsedChars() + " totalChars: " + maxCharsByShopName);
                    return new BaseResponse<>().CreateSuccessResponse(map);
                }
            } catch (Exception e) {
                // 日志记录错误，便于后续排查
                appInsights.trackTrace("getUserLimitChars Error while getUserLimitChars for shop " + e.getMessage());
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
    public BaseResponse<Object> getUserSubscriptionPlan(@RequestParam String shopName) {
        //判断shopName的值是否有
        if (shopName == null || shopName.isEmpty()) {
            return new BaseResponse<>().CreateErrorResponse("shopName is null");
        }
        SubscriptionVO subscriptionVO = new SubscriptionVO();
        UserSubscriptionsDO userSubscriptionsDO = userSubscriptionsService.getOne(new LambdaQueryWrapper<UserSubscriptionsDO>().eq(UserSubscriptionsDO::getShopName, shopName));
        if (userSubscriptionsDO == null) {
            appInsights.trackTrace("getUserSubscriptionPlan 用户获取的数据失败： " + shopName);
            return new BaseResponse<>().CreateErrorResponse("userSubscriptionsDO is null");
        }
        if (userSubscriptionsDO.getFeeType() == null ){
            userSubscriptionsDO.setFeeType(0);
        }
        Integer userSubscriptionPlan = userSubscriptionsDO.getPlanId();
        subscriptionVO.setUserSubscriptionPlan(userSubscriptionPlan);

        if ("ciwishop.myshopify.com".equals(shopName)) {
            subscriptionVO.setUserSubscriptionPlan(6);
            subscriptionVO.setCurrentPeriodEnd(null);
            subscriptionVO.setFeeType(userSubscriptionsDO.getFeeType());
            return new BaseResponse<>().CreateSuccessResponse(subscriptionVO);
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
        CharsOrdersDO charsOrdersDO = charsOrdersService.getOne(new QueryWrapper<CharsOrdersDO>()
                .select(
                        "TOP 1 id"
                )
                .eq("shop_name", shopName)
                .like("id", "AppSubscription")
                .eq("status", "ACTIVE")
                .orderByDesc("updated_date")
        );

        if (charsOrdersDO == null) {
            return new BaseResponse<>().CreateErrorResponse("charsOrdersDO is null");
        }
        UsersDO usersDO = usersService.getOne(new QueryWrapper<UsersDO>()
                .eq("shop_name", shopName)
        );

        //通过charsOrdersDO的id，获取信息
        String query = getSubscriptionQuery(charsOrdersDO.getId());
        String infoByShopify;
        String env = System.getenv("ApplicationEnv");
        //根据新的集合获取这个订阅计划的信息
        if ("prod".equals(env) || "dev".equals(env)) {
            infoByShopify = String.valueOf(getInfoByShopify(new ShopifyRequest(usersDO.getShopName(), usersDO.getAccessToken(), API_VERSION_LAST, null), query));
        } else {
            infoByShopify = getShopifyDataByCloud(new CloudServiceRequest(usersDO.getShopName(), usersDO.getAccessToken(), API_VERSION_LAST, "en", query));
        }
        if (infoByShopify == null || infoByShopify.isEmpty()) {
            subscriptionVO.setFeeType(0);
            subscriptionVO.setUserSubscriptionPlan(2);
            subscriptionVO.setCurrentPeriodEnd(null);
            return new BaseResponse<>().CreateSuccessResponse(subscriptionVO);
        }
        //根据订阅计划信息，判断是否是第一个月的开始
        JSONObject root = JSON.parseObject(infoByShopify);
        JSONObject node = root.getJSONObject("node");
        if (node == null || node.isEmpty()) {
            //用户卸载，计划会被取消，但不确定其他情况
            subscriptionVO.setFeeType(0);
            subscriptionVO.setUserSubscriptionPlan(2);
            subscriptionVO.setCurrentPeriodEnd(null);
        }else {
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
        if (registerTransactionRequest.isEmpty()){
            return new BaseResponse<>().CreateErrorResponse("registerTransactionRequest is empty");
        }
        String s = shopifyService.updateShopifyDataByTranslateTextRequests(registerTransactionRequest);
        appInsights.trackTrace("updateItems 用户 " + registerTransactionRequest.get(0).getShopName() + " 返回数据 response: " + s);
        if (s.contains("\"value\":")) {
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
