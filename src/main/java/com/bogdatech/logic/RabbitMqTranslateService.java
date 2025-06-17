package com.bogdatech.logic;

import com.bogdatech.context.TranslateContext;
import com.bogdatech.entity.DO.TranslateResourceDTO;
import com.bogdatech.entity.VO.RabbitMqTranslateVO;
import com.bogdatech.exception.ClientException;
import com.bogdatech.model.controller.request.CloudServiceRequest;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.requestBody.ShopifyRequestBody;
import com.bogdatech.utils.CharacterCountUtils;
import com.bogdatech.utils.TypeConversionUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

import static com.bogdatech.constants.RabbitMQConstants.USER_TRANSLATE_EXCHANGE;
import static com.bogdatech.constants.RabbitMQConstants.USER_TRANSLATE_ROUTING_KEY;
import static com.bogdatech.constants.TranslateConstants.*;
import static com.bogdatech.entity.DO.TranslateResourceDTO.ALL_RESOURCES;
import static com.bogdatech.enums.ErrorEnum.SHOPIFY_RETURN_ERROR;
import static com.bogdatech.integration.ShopifyHttpIntegration.getInfoByShopify;
import static com.bogdatech.logic.ShopifyService.getShopifyDataByCloud;
import static com.bogdatech.logic.TranslateService.getShopifyData;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.TypeConversionUtils.convertTranslateRequestToShopifyRequest;
import static com.bogdatech.utils.TypeConversionUtils.translateContextToRabbitMqTranslateVO;

@Service
public class RabbitMqTranslateService {

    private final AmqpTemplate amqpTemplate;

    @Autowired
    public RabbitMqTranslateService(AmqpTemplate amqpTemplate) {
        this.amqpTemplate = amqpTemplate;
    }

    /**
     * MQ翻译
     * 读取该用户的shopify数据，然后循环获取模块数据
     * */
    public void mqTranslate(TranslateRequest request){
        ShopifyRequest shopifyRequest = convertTranslateRequestToShopifyRequest(request);
        for (TranslateResourceDTO translateResource : ALL_RESOURCES
        ) {
            if (translateResource.getResourceType().equals(SHOP_POLICY) || translateResource.getResourceType().equals(PAYMENT_GATEWAY)) {
                continue;
            }

            System.out.println("正在翻译： " + translateResource.getResourceType());
            translateResource.setTarget(request.getTarget());
            String shopifyData = getShopifyData(shopifyRequest, translateResource);
            CharacterCountUtils counter = new CharacterCountUtils();
            Integer remainingChars = 0;
            Map<String, Object> glossaryMap = new HashMap<>();
            String languagePackId = null;
            boolean handleFlag = false;
            TranslateContext translateContext = new TranslateContext(shopifyData, shopifyRequest, translateResource, counter, remainingChars, glossaryMap, request.getSource(), languagePackId, null, handleFlag);

            // MQ发送逻辑
            parseShopifyData(translateContext);

        }
        //TODO：当模块都发送后，发送邮件模块
        System.out.println("翻译结束");
    }

    /**
     * 解析shopifyData的数据，递归获取，每250条数据作为一个翻译任务发送到队列里面
     * */
    public void parseShopifyData(TranslateContext translateContext){
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode;
        try {
            rootNode = objectMapper.readTree(translateContext.getShopifyData());
        } catch (JsonProcessingException e) {
            appInsights.trackTrace("rootNode errors： " + e.getMessage());
            return;
        }

        //根据模块选择方法调用。
        RabbitMqTranslateVO rabbitMqTranslateVO = translateContextToRabbitMqTranslateVO(translateContext);
        amqpTemplate.convertAndSend(USER_TRANSLATE_EXCHANGE, USER_TRANSLATE_ROUTING_KEY + translateContext.getShopifyRequest().getShopName(), rabbitMqTranslateVO);

        translateNextPage(rootNode, translateContext);
    }

    //获取下一页数据
    public void translateNextPage(JsonNode rootNode, TranslateContext translateContext) {
        // 获取translatableResources节点
        JsonNode translatableResourcesNode = rootNode.path("translatableResources");
        // 获取pageInfo节点
        JsonNode pageInfoNode = translatableResourcesNode.path("pageInfo");
        if (translatableResourcesNode.hasNonNull("pageInfo")) {
            if (pageInfoNode.hasNonNull("hasNextPage") && pageInfoNode.get("hasNextPage").asBoolean()) {
                JsonNode endCursor = pageInfoNode.path("endCursor");
                System.out.println("获取下一页： " + endCursor);
                translateContext.getTranslateResource().setAfter(endCursor.asText(null));
                translateNextPageData(translateContext, translateContext.getCharacterCountUtils());
            }
        }
    }

    //递归处理下一页数据
    private void translateNextPageData(TranslateContext translateContext, CharacterCountUtils usedCharCounter) {
        JsonNode nextPageData;
        try {
            nextPageData = fetchNextPage(translateContext.getTranslateResource(), translateContext.getShopifyRequest());
            if (nextPageData == null) {
                return;
            }
        } catch (Exception e) {
            return;
        }
        translateContext.setShopifyData(nextPageData.toString());
        // 重新开始翻译流程
        parseShopifyData(translateContext);
    }

    public JsonNode fetchNextPage(TranslateResourceDTO translateResource, ShopifyRequest request) {
        CloudServiceRequest cloudServiceRequest = TypeConversionUtils.shopifyToCloudServiceRequest(request);
        String query = new ShopifyRequestBody().getAfterQuery(translateResource);
        cloudServiceRequest.setBody(query);
        return getShopifyJsonNode(request, cloudServiceRequest, query);
    }

    public static JsonNode getShopifyJsonNode(ShopifyRequest request, CloudServiceRequest cloudServiceRequest, String query) {
        String env = System.getenv("ApplicationEnv");
        String infoByShopify;
        if ("prod".equals(env) || "dev".equals(env)) {
            infoByShopify = String.valueOf(getInfoByShopify(request, query));
        } else {
            infoByShopify = getShopifyDataByCloud(cloudServiceRequest);
        }
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            if (infoByShopify == null || infoByShopify.isEmpty()) {
                return null;
            }
            return objectMapper.readTree(infoByShopify);
        } catch (JsonProcessingException e) {
            throw new ClientException(SHOPIFY_RETURN_ERROR.getErrMsg());
        }
    }

    //根据模块选择翻译方法
    public static void translateByModeType(RabbitMqTranslateVO rabbitMqTranslateVO){
        String modelType = rabbitMqTranslateVO.getModeType();
        switch (modelType){
            case ONLINE_STORE_THEME:
            case ONLINE_STORE_THEME_APP_EMBED:
            case ONLINE_STORE_THEME_JSON_TEMPLATE:
            case ONLINE_STORE_THEME_SECTION_GROUP:
            case ONLINE_STORE_THEME_SETTINGS_CATEGORY:
            case ONLINE_STORE_THEME_SETTINGS_DATA_SECTIONS:
            case ONLINE_STORE_THEME_LOCALE_CONTENT:
                //翻译主题
                break;
            case PRODUCT:
                //产品
                break;
            case PRODUCT_OPTION:
                //产品设置
                break;
            case PRODUCT_OPTION_VALUE:
                //产品设置值
                break;
            case PAGE:
                //页面
                break;
            case ARTICLE:
            case BLOG:
                //文章
                break;
            case METAFIELD:
                //元字段
                break;
            case METAOBJECT:
                //元对象
                break;
            case EMAIL_TEMPLATE:
                //邮件
                break;
            case COLLECTION:
            case PACKING_SLIP_TEMPLATE:
            case MENU:
            case LINK:
            case DELIVERY_METHOD_DEFINITION:
            case FILTER:
            case PAYMENT_GATEWAY:
            case SELLING_PLAN:
            case SELLING_PLAN_GROUP:
            case SHOP:
            //普通翻译
                break;
            default:
                break;
        }
    }
}
