package com.bogdatech.logic;

import com.bogdatech.entity.TranslateResourceDTO;
import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.integration.TestingEnvironmentIntegration;
import com.bogdatech.model.controller.request.CloudServiceRequest;
import com.bogdatech.model.controller.request.RegisterTransactionRequest;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.model.controller.request.TranslationCounterRequest;
import com.bogdatech.query.ShopifyQuery;
import com.bogdatech.repository.JdbcRepository;
import com.bogdatech.utils.CharacterCountUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.HashMap;
import java.util.Map;

import static com.bogdatech.entity.TranslateResourceDTO.translationResources;

@Component
public class ShopifyService {

    @Autowired
    private ShopifyHttpIntegration shopifyApiIntegration;

    @Autowired
    private JdbcRepository jdbcRepository;

    @Autowired
    private TestingEnvironmentIntegration testingEnvironmentIntegration;

    private final TelemetryClient appInsights = new TelemetryClient();
    ShopifyQuery shopifyQuery = new ShopifyQuery();

    //封装调用云服务器实现获取shopify数据的方法
    public String getShopifyData(CloudServiceRequest cloudServiceRequest) {
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

    //获得翻译前一共需要消耗的字符数
    public int getTotalWords(ShopifyRequest request) {
        CharacterCountUtils counter = new CharacterCountUtils();
        CloudServiceRequest cloudServiceRequest = new CloudServiceRequest();
        cloudServiceRequest.setShopName(request.getShopName());
        cloudServiceRequest.setAccessToken(request.getAccessToken());
        cloudServiceRequest.setTarget(request.getTarget());
        for (TranslateResourceDTO translateResource : translationResources) {
            translateResource.setTarget(request.getTarget());
            String query = shopifyQuery.getFirstQuery(translateResource);
            //调用逻辑 本地-》 云服务器 -》 云服务器 -》shopify -》本地
            cloudServiceRequest.setBody(query);
            String infoByShopify = getShopifyData(cloudServiceRequest);
//            System.out.println(infoByShopify);
            countBeforeTranslateChars(infoByShopify, request, translateResource, counter);
        }
        appInsights.trackTrace(request + "最终使用的值： " + counter.getTotalChars());
        return counter.getTotalChars();
    }

    @Async
    public void countBeforeTranslateChars(String infoByShopify, ShopifyRequest request, TranslateResourceDTO translateResource, CharacterCountUtils counter) {
        appInsights.trackTrace("现在翻译到： " + translateResource.getResourceType());
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode translatedRootNode = null;
        try {
            JsonNode rootNode = objectMapper.readTree(infoByShopify);
            translatedRootNode = translateSingleLineTextFieldsRecursively(rootNode, request, counter);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        // 递归处理下一页数据
        handlePagination(translatedRootNode, request, counter, translateResource);
        //打印最后使用的值
        System.out.println("最后使用的值： " + counter.getTotalChars());
        appInsights.trackTrace(request + "最后使用的值： " + counter.getTotalChars());
        jdbcRepository.updateUsedCharsByShopName(new TranslationCounterRequest(0, request.getShopName(), 0, counter.getTotalChars(), 0, 0, 0));
    }

    //递归遍历JSON树：使用 translateSingleLineTextFieldsRecursively 方法递归地遍历整个 JSON 树，并对 translatableContent 字段进行特别处理。
    private JsonNode translateSingleLineTextFieldsRecursively(JsonNode node, ShopifyRequest request, CharacterCountUtils counter) {
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            node.fieldNames().forEachRemaining(fieldName -> {
                JsonNode fieldValue = node.get(fieldName);
                if ("resourceId".equals(fieldName)) {
                    counter.addChars(1);
                }
                if ("translatableContent".equals(fieldName)) {

//                    translateSingleLineTextFields((ArrayNode) fieldValue, request, counter);
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

    //对符合条件的 value 进行计数
    private void translateSingleLineTextFields(ArrayNode contentNode, ShopifyRequest request, CharacterCountUtils counter) {
        for (JsonNode contentItem : contentNode) {
            ObjectNode contentItemNode = (ObjectNode) contentItem;
            //打印当前遍历的值 为什么部分不翻译
            // 跳过 key 为 "handle" 的项
            if ("handle".equals(contentItemNode.get("key").asText())) {
                continue;  // 跳过当前项
            }
            // 获取 value
            String value = contentItemNode.get("value").asText();
            System.out.println("当前遍历的值: " + value);
            System.out.println("当前遍历的值的字符数: " + value.length());
            try {
//                String encodedQuery = URLEncoder.encode(value, StandardCharsets.UTF_8);
                counter.addChars(value.length());
                System.out.println("当前遍历的值的计数器的值: " + counter.getTotalChars());
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    // 递归处理下一页数据
    private JsonNode handlePagination(JsonNode translatedRootNode, ShopifyRequest request, CharacterCountUtils counter, TranslateResourceDTO translateResourceDTO) {
        // 获取translatableResources节点
        JsonNode translatableResourcesNode = translatedRootNode.path("translatableResources");
        // 获取pageInfo节点
        JsonNode pageInfoNode = translatableResourcesNode.path("pageInfo");

        if (translatableResourcesNode.hasNonNull("pageInfo")) {
            if (pageInfoNode.hasNonNull("hasNextPage") && pageInfoNode.get("hasNextPage").asBoolean()) {
                JsonNode endCursor = pageInfoNode.get("endCursor");
                translateResourceDTO.setAfter(endCursor.asText());
                translatedRootNode = translateNextPage(request, counter, translateResourceDTO);
            }
        }
        return translatedRootNode;
    }

    //递归处理下一页数据
    private JsonNode translateNextPage(ShopifyRequest request, CharacterCountUtils counter, TranslateResourceDTO
            translateResource) {
        JsonNode nextPageData = fetchNextPage(translateResource, request);
        // 重新开始翻译流程
        JsonNode translatedNextPage = translateSingleLineTextFieldsRecursively(nextPageData, request, counter);
        // 递归处理下一页数据
        JsonNode translatableResourcesNode = translatedNextPage.path("translatableResources");
        if (translatableResourcesNode.hasNonNull("pageInfo")) {
            JsonNode pageInfo = translatableResourcesNode.path("pageInfo");
            if (pageInfo.hasNonNull("hasNextPage") && pageInfo.path("hasNextPage").asBoolean()) {
                JsonNode newEndCursor = pageInfo.path("endCursor");
                translateResource.setAfter(newEndCursor.asText());
                return translateNextPage(request, counter, translateResource);
            }
        }
        return translatedNextPage;
    }

    //修改getTestQuery里面的testQuery，用获取后的的查询语句进行查询
    public JsonNode fetchNextPage(TranslateResourceDTO translateResource, ShopifyRequest request) {
        CloudServiceRequest cloudServiceRequest = new CloudServiceRequest();
        cloudServiceRequest.setShopName(request.getShopName());
        cloudServiceRequest.setAccessToken(request.getAccessToken());
        cloudServiceRequest.setTarget(request.getTarget());

        ShopifyQuery shopifyQuery = new ShopifyQuery();
        String query = shopifyQuery.getAfterQuery(translateResource);
        cloudServiceRequest.setBody(query);
        String infoByShopify = getShopifyData(cloudServiceRequest);
        if (infoByShopify == null) {
            throw new IllegalArgumentException("Argument 'content' cannot be null or empty");
        }
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode;
        try {
            rootNode = objectMapper.readTree(infoByShopify);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return rootNode;
    }

    //修改单个文本数据
    public String updateShopifySingleData(@RequestBody RegisterTransactionRequest registerTransactionRequest) {
        ShopifyRequest shopifyRequest = new ShopifyRequest();
        shopifyRequest.setShopName(registerTransactionRequest.getShopName());
        shopifyRequest.setAccessToken(registerTransactionRequest.getAccessToken());
        shopifyRequest.setTarget(registerTransactionRequest.getTarget());
        Map<String, Object> variables = new HashMap<>();
        Map<String, Object> translation = new HashMap<>();
        translation.put("locale", registerTransactionRequest.getTarget());
        translation.put("key", registerTransactionRequest.getKey());
        translation.put("translatableContentDigest", registerTransactionRequest.getTranslatableContentDigest());
        Object[] translations = new Object[]{
                translation // 将HashMap添加到数组中
        };
        variables.put("translations", translations);
        String string = shopifyApiIntegration.registerTransaction(shopifyRequest, variables);
        return string;
    }

}
