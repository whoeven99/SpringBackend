package com.bogdatech.logic;


import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bogdatech.entity.TranslatesDO;
import com.bogdatech.exception.ClientException;
import com.bogdatech.integration.TranslateApiIntegration;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.model.controller.request.TranslationCounterRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.repository.JdbcRepository;
import com.bogdatech.utils.CharacterCountUtils;
import com.bogdatech.utils.StringUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import static com.bogdatech.enums.ErrorEnum.TRANSLATE_ERROR;

@Component
@EnableAsync
public class TranslateService {

    @Autowired
    private TranslateApiIntegration translateApiIntegration;

    @Autowired
    private JdbcRepository jdbcRepository;

//    @Autowired
//    private CharacterCountUtils counter;

    // 构建URL
    public BaseResponse translate(TranslatesDO request) {
        return new BaseResponse().CreateSuccessResponse(null);
    }

    public BaseResponse baiDuTranslate(TranslateRequest request) {
        String result = translateApiIntegration.baiDuTranslate(request);
        if (result != null) {
            return new BaseResponse().CreateSuccessResponse(result);
        }
        return new BaseResponse<>().CreateErrorResponse(TRANSLATE_ERROR);
    }

    public BaseResponse googleTranslate(TranslateRequest request) {
        String result = translateApiIntegration.googleTranslate(request);
        if (result != null) {
            return new BaseResponse().CreateSuccessResponse(result);
        }
        return new BaseResponse<>().CreateErrorResponse(TRANSLATE_ERROR);
    }


    @Async
    public void test(TranslatesDO request) {
        System.out.println("我要翻译了" + Thread.currentThread().getName());
        //睡眠1分钟
        try {
            Thread.sleep(1000 * 60 * 1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("翻译完成" + Thread.currentThread().getName());
        //更新状态
        jdbcRepository.updateTranslateStatus(request.getId(), 0);
    }


    //写死的json
    public BaseResponse userBDTranslateJsonObject() {
        PathMatchingResourcePatternResolver resourceLoader = new PathMatchingResourcePatternResolver();
        JSONObject data = null;
        try {
            Resource resource = resourceLoader.getResource("classpath:jsonData/project.json");
            InputStream inputStream = resource.getInputStream();
            ObjectMapper objectMapper = new ObjectMapper();
            data = objectMapper.readValue(inputStream, JSONObject.class);
//            System.out.println(data);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 对options节点进行递归翻译处理
        translateOptions(data.getJSONObject("products"));
        // 返回成功响应
        return new BaseResponse<>().CreateSuccessResponse(data);
    }

    private void translateOptions(JSONObject options) {
        if (options.containsKey("values")) {
            JSONArray values = options.getJSONArray("values");
            JSONArray translatedValues = new JSONArray();

            for (Object valueObj : values) {
                String value = (String) valueObj;
                value = StringUtil.replaceSpaces(value, "-");
                try {
                    // 调用翻译接口
                    String translatedValue = translateApiIntegration.baiDuTranslate(new TranslateRequest(0, null, null, "en", "zh", value));
                    translatedValues.add(translatedValue);
                } catch (Exception e) {
                    // 如果翻译失败，则直接返回错误响应
                    throw new RuntimeException("Translation failed", e);
                }
            }

            // 替换原values值为翻译后的值
            options.put("values", translatedValues);
        }

        // 递归处理options内的其他JSON对象
        for (String key : options.keySet()) {
            Object value = options.get(key);
            if (value instanceof JSONObject) {
                translateOptions((JSONObject) value);
            } else if (value instanceof JSONArray) {
                for (Object item : (JSONArray) value) {
                    if (item instanceof JSONObject) {
                        translateOptions((JSONObject) item);
                    }
                }
            }
        }
    }

    //模拟测试环境读取json数据，需要翻墙的数据都为死数据
    public JsonNode readJsonFile() {
        //模拟传入shopifyRequest
        ShopifyRequest request = new ShopifyRequest();
        request.setTarget("jp");
        request.setShopName("quickstart-0f992326.myshopify.com");
        //模拟传入了计数器
        CharacterCountUtils counter = new CharacterCountUtils();
        BaseResponse response = jdbcRepository.readCharsByShopName(new TranslationCounterRequest(0, request.getShopName(), 0));
        ArrayList<TranslationCounterRequest> response1 = (ArrayList<TranslationCounterRequest>) response.getResponse();
        System.out.println("response1 = " + response1.get(0).getChars());
        counter.addChars(response1.get(0).getChars());
        PathMatchingResourcePatternResolver resourceLoader = new PathMatchingResourcePatternResolver();
        JsonNode translatedRootNode = null;
        try {
            Resource resource = resourceLoader.getResource("classpath:jsonData/produck.json");
            InputStream inputStream = resource.getInputStream();
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(inputStream);
            translatedRootNode = translateSingleLineTextFieldsRecursively(rootNode, request, counter);
            String translatedJsonString = translatedRootNode.toString();
            System.out.println("Translated JSON:\n" + translatedJsonString);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("counter: " + counter.getTotalChars());
        jdbcRepository.updateCharsByShopName(new TranslationCounterRequest(0, request.getShopName(), counter.getTotalChars()));
        System.out.println("counter: " + counter.getTotalChars());
        return translatedRootNode;
    }

    //根据返回的json片段，将符合条件的value翻译,并返回json片段
    public JsonNode translateJson(JSONObject objectData, ShopifyRequest request){
        //从数据库获取已使用的字符数据，放入计数器中
        CharacterCountUtils counter = new CharacterCountUtils();
        BaseResponse response = jdbcRepository.readCharsByShopName(new TranslationCounterRequest(0, request.getShopName(), 0));
        ArrayList<TranslationCounterRequest> response1 = (ArrayList<TranslationCounterRequest>) response.getResponse();
        System.out.println("response1 = " + response1.get(0).getChars());
        counter.addChars(response1.get(0).getChars());

        if (objectData == null) {
            throw new IllegalArgumentException("Argument 'content' cannot be null or empty.xxxxxxxxx");
        }
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode translatedRootNode = null;
        try {
            JsonNode rootNode = objectMapper.readTree(objectData.toJSONString());
            translatedRootNode = translateSingleLineTextFieldsRecursively(rootNode, request, counter);
            String translatedJsonString = translatedRootNode.toString();
            System.out.println("Translated JSON:\n" + translatedJsonString);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        System.out.println("counter: " + counter.getTotalChars());
        jdbcRepository.updateCharsByShopName(new TranslationCounterRequest(0, request.getShopName(), counter.getTotalChars()));
        System.out.println("counter: " + counter.getTotalChars());
        return translatedRootNode;
    }
    //根据key找value
    private JsonNode translateSingleLineTextFieldsRecursively(JsonNode node, ShopifyRequest request, CharacterCountUtils counter) {
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            node.fieldNames().forEachRemaining(fieldName -> {
                JsonNode fieldValue = node.get(fieldName);
                if ("translatableContent".equals(fieldName)) {
                    if (counter.getTotalChars() <= 0){
                        jdbcRepository.updateCharsByShopName(new TranslationCounterRequest(0, request.getShopName(), counter.getTotalChars()));
                        System.out.println("达到字符限制，终止循环.");
                        throw new ClientException("翻译字符数超过限制.");
                    }
                    ArrayNode translatedContent = translateSingleLineTextFields((ArrayNode) fieldValue, request, counter);
                    objectNode.set(fieldName, translatedContent);
                } else {
                    objectNode.set(fieldName, translateSingleLineTextFieldsRecursively(fieldValue, request, counter));
                }
            });
        } else if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (int i = 0; i < node.size(); i++) {
                JsonNode element = node.get(i);
                arrayNode.set(i, translateSingleLineTextFieldsRecursively(element, request, counter));
            }
        }
        return node;
    }

    //找到符合条件的value翻译
    private ArrayNode translateSingleLineTextFields(ArrayNode contentNode, ShopifyRequest request, CharacterCountUtils counter) {
        ArrayNode translatedContent = new ObjectMapper().createArrayNode();
        contentNode.forEach(contentItem -> {
            ObjectNode contentItemNode = (ObjectNode) contentItem;
            if ("SINGLE_LINE_TEXT_FIELD".equals(contentItemNode.get("type").asText())
                    || "MULTI_LINE_TEXT_FIELD".equals(contentItemNode.get("type").asText())) {
                String value = contentItemNode.get("value").asText();
                String source = contentItemNode.get("locale").asText();
                //如果value为空，则不翻译
                if (value == null || value.isEmpty()) {
                    return;
                }
                try {
                    counter.subtractChars(value.length());
                    System.out.println("target: " + request.getTarget());
                    String translatedValue = translateApiIntegration.baiDuTranslate(new TranslateRequest(0, null, null, source, request.getTarget(), value));
                    contentItemNode.put("value", translatedValue);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            translatedContent.add(contentItem);
        });
        return translatedContent;
    }


}
