package com.bogdatech.logic;

import com.alibaba.fastjson.JSONObject;
import com.bogdatech.entity.TranslateResourceDTO;
import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.query.ShopifyQuery;
import com.bogdatech.repository.JdbcRepository;
import com.bogdatech.utils.CharacterCountUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.bogdatech.entity.TranslateResourceDTO.translationResources;

@Component
public class ShopifyService {

    @Autowired
    private ShopifyHttpIntegration shopifyApiIntegration;

    @Autowired
    private JdbcRepository jdbcRepository;

    ShopifyQuery shopifyQuery = new ShopifyQuery();

    //获得翻译前一共需要消耗的字符数
    public void getTotalWords(ShopifyRequest request){
        CharacterCountUtils counter = new CharacterCountUtils();
        for (TranslateResourceDTO translateResource : translationResources) {
            translateResource.setTarget(request.getTarget());
            String query = shopifyQuery.getFirstQuery(translateResource);
            //调用逻辑 本地-》 云服务器 -》 云服务器 -》shopify -》本地
            JSONObject infoByShopify = shopifyApiIntegration.getInfoByShopify(request, query);
//            countBeforeTranslateChars(infoByShopify, request, translateResource, counter);
        }
    }
}
