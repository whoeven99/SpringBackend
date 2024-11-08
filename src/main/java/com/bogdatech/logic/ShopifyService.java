package com.bogdatech.logic;

import com.bogdatech.entity.TranslateResourceDTO;
import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.integration.TestingEnvironmentIntegration;
import com.bogdatech.model.controller.request.CloudServiceRequest;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.query.ShopifyQuery;
import com.bogdatech.utils.CharacterCountUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.bogdatech.entity.TranslateResourceDTO.translationResources;

@Component
public class ShopifyService {

    @Autowired
    private ShopifyHttpIntegration shopifyApiIntegration;

    @Autowired
    private TestingEnvironmentIntegration testingEnvironmentIntegration;


    ShopifyQuery shopifyQuery = new ShopifyQuery();

    //封装调用云服务器实现获取shopify数据的方法
        public String getShopifyData(CloudServiceRequest cloudServiceRequest){
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
    public void getTotalWords(ShopifyRequest request){
        CharacterCountUtils counter = new CharacterCountUtils();
        CloudServiceRequest cloudServiceRequest = new CloudServiceRequest();
        cloudServiceRequest.setShopName(request.getShopName());
        cloudServiceRequest.setTarget(request.getTarget());
        for (TranslateResourceDTO translateResource : translationResources) {
            translateResource.setTarget(request.getTarget());
            String query = shopifyQuery.getFirstQuery(translateResource);
            //调用逻辑 本地-》 云服务器 -》 云服务器 -》shopify -》本地
            cloudServiceRequest.setBody(query);
            String infoByShopify = getShopifyData(cloudServiceRequest);
            System.out.println(infoByShopify);
//            countBeforeTranslateChars(infoByShopify, request, translateResource, counter);
        }
    }
}
