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
import com.bogdatech.utils.CharacterCountUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     *  先调用shopify的API，获得数据后再通过百度翻译API翻译数据
     */
    @PostMapping("/translate/translateJson")
    public BaseResponse translateJson(@RequestBody ShopifyRequest shopifyRequest) {
        return new BaseResponse<>().CreateSuccessResponse(translateService.translateJson(shopifyApiIntegration.getInfoByShopify(shopifyRequest, ShopifyQuery.PRODUCT2_QUERY), shopifyRequest, new TranslateResourceDTO()));
    }

    /*
     *  通过TranslateResourceDTO获取定义好的数组，对其进行for循环，遍历获得query，通过发送shopify的API获得数据，获得数据后再通过百度翻译API翻译数据
     */
    @GetMapping("testFor")
    public void test(@RequestBody ShopifyRequest shopifyRequest) {
        JSONObject objectData = new JSONObject();
        JsonNode jsonNode = null;
        for (TranslateResourceDTO translateResource : translationResources) {
            TestQuery testQuery = new TestQuery();
            translateResource.setTarget(shopifyRequest.getTarget());
            String query = testQuery.getTestQuery(translateResource);
            appInsights.trackTrace(query);
            JSONObject infoByShopify = shopifyApiIntegration.getInfoByShopify(shopifyRequest, query);
            objectData.put(translateResource.getResourceType(), infoByShopify);
            jsonNode = translateService.translateJson(infoByShopify, shopifyRequest, translateResource);
        }
        appInsights.trackTrace("objectData: " + objectData);
        appInsights.trackTrace("jsonNode: " + jsonNode);
    }

    //测试计数器会不会因为同时调用接口而产生不同的数据
    @GetMapping("testCount")
    public void testCount() {
        CharacterCountUtils counter = new CharacterCountUtils();
        for (int i = 0; i < 50; i++) {
            counter.addChars(1);
            //睡眠1秒
            try {
                Thread.sleep(100);
                System.out.println(Thread.currentThread().getName() + " Count: " + counter.getTotalChars() + " Thread ID: " + Thread.currentThread().getId() + " Time: " + System.currentTimeMillis());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println("Count1: " + counter.getTotalChars());
        counter.reset();
        System.out.println("Count2: " + counter.getTotalChars());
    }

    @PostMapping("testUpdate")
    public void testUpdate(@RequestBody ShopifyRequest shopifyRequest) {
//        // 构建GraphQL请求体
        Map<String, Object> variables = new HashMap<>();
        variables.put("resourceId", "gid://shopify/DeliveryMethodDefinition/634794770682");
//        variables.put("translations", new Object[]{
//                new HashMap<String, Object>() {{
//                    put("locale", "ja");
//                    put("key", "name");
//                    put("value", "経済");
//                    put("translatableContentDigest", "c1d078d516dd7c8b81f32827069816db43529642eb82f071a8b2ffa261567dda");
//                }}
//        });
        // 步骤1: 定义HashMap
        Map<String, Object> translation = new HashMap<>();
        translation.put("locale", "ja"); // 语言环境
        translation.put("key", "name");   // 翻译的键
        translation.put("value", "経済"); // 翻译的值
        translation.put("translatableContentDigest", "c1d078d516dd7c8b81f32827069816db43529642eb82f071a8b2ffa261567dda"); // 内容摘要

        // 步骤2: 创建数组
        Object[] translations = new Object[]{
                translation // 将HashMap添加到数组中
        };

        // 步骤3: 存储到变量
        variables.put("translations", translations);
        System.out.println("variables: " +  new JSONObject(variables));
        // 输出variables的所有信息
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            System.out.println("Key: " + entry.getKey() + ", Value: " + entry.getValue());

            // 如果Value是一个数组，我们可以进一步遍历这个数组
            if (entry.getValue() instanceof Object[]) {
                Object[] array = (Object[]) entry.getValue();
                for (int i = 0; i < array.length; i++) {
                    Object item = array[i];
                    if (item instanceof Map) {
                        // 如果数组中的元素是一个Map，我们也可以遍历这个Map
                        Map<?, ?> mapItem = (Map<?, ?>) item;
                        System.out.println("  Item " + i + ":");
                        for (Map.Entry<?, ?> subEntry : mapItem.entrySet()) {
                            System.out.println("    Key: " + subEntry.getKey() + ", Value: " + subEntry.getValue());
                        }
                    } else {
                        System.out.println("  Item " + i + ": " + item);
                    }
                }
            }
        }

//        String string = shopifyApiIntegration.registerTransaction(shopifyRequest, variables);
//        System.out.println("string: " + string);
    }
}
