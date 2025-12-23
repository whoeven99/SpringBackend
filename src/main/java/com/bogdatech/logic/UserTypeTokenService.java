package com.bogdatech.logic;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.Service.IUserTypeTokenService;
import com.bogdatech.entity.DO.TranslateResourceDTO;
import com.bogdatech.entity.DO.UserTypeTokenDO;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.model.controller.request.TranslateRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;

import static com.bogdatech.constants.TranslateConstants.SHOP_NAME;
import static com.bogdatech.entity.DO.TranslateResourceDTO.TOKEN_MAP;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.TypeConversionUtils.convertTranslateRequestToShopifyRequest;

@Component
@EnableAsync
public class UserTypeTokenService {
    @Autowired
    private IUserTypeTokenService userTypeTokenService;
    @Autowired
    private ITranslatesService translatesService;
    @Autowired
    private ShopifyService shopifyService;

    /**
     * 调用方法获取数据库Translates里面的id值，根据id值从UserTypeToken表获取对应的数据
     * @param request 用户数据对象，包含用户信息（如shopName、target、source, accessToken）
     * @return UserTypeTokenDO  UserTypeTokenDO数据类型
     */
    public UserTypeTokenDO getUserTypeToken(TranslateRequest request) {
        Integer translationId = translatesService.getIdByShopNameAndTargetAndSource(request.getShopName(), request.getTarget(), request.getSource());
        if (translationId != null){
            return userTypeTokenService.getOne(new QueryWrapper<UserTypeTokenDO>().eq("translation_id", translationId));
        }
        return null;
    }

    /**
     * 调用方法向数据库UserTypeToken的插入初始化数据，根据shopName向UserTypeToken表插入对应的数据
     * @param request 用户数据对象，包含用户信息（如shopName、target、source, accessToken）
     *
     */
    @Async
    public void getUserInitToken(TranslateRequest request) {
        UserTypeTokenDO userTypeTokenDO = userTypeTokenService.getOne(new QueryWrapper<UserTypeTokenDO>().eq(SHOP_NAME, request.getShopName()));
        if (userTypeTokenDO == null){

            ShopifyRequest shopifyRequest = convertTranslateRequestToShopifyRequest(request);
            //将shopName初始值存储到数据库中
            userTypeTokenService.insertInitial(shopifyRequest.getShopName());

            //循环type获取token
            for (String key : TOKEN_MAP.keySet()
            ) {
                try {
                    shopifyService.insertInitialByTranslation(shopifyRequest, key, "initial");
                } catch (Exception e) {
                    appInsights.trackTrace("getUserInitToken " + shopifyRequest.getShopName() + " " + key + "模块获取失败： " + request);
                }
            }

        }
    }

    /**
     * 调用方法向数据库UserTypeToken的插入初始化数据，根据shopName向UserTypeToken表插入对应的数据
     * @param shopName 用户的商店名
     * @return UserTypeTokenDO  UserTypeTokenDO数据类型
     */
    public UserTypeTokenDO getUserInitTokenByShopName(String shopName) {
        return userTypeTokenService.getOne(new QueryWrapper<UserTypeTokenDO>().eq(SHOP_NAME, shopName));
    }

    /**
     * 根据request获取对应模块的token。如果status为2就不计数，如果为其他就开始计数
     *
     * @param request 请求对象，包含shopName、target、source，accessToken等信息
     */
    @Async
    public void startTokenCount(TranslateRequest request) {
        try {
            //获取translationId
            Integer translationId;
            translationId = translatesService.getIdByShopNameAndTargetAndSource(request.getShopName(), request.getTarget(), request.getSource());
            if (translationId == null) {
                //添加这个翻译项
                //插入语言状态
                translatesService.insertLanguageStatus(request);
                translationId = translatesService.getIdByShopNameAndTargetAndSource(request.getShopName(), request.getTarget(), request.getSource());
            }
            //判断数据库中UserTypeToken中translationId对应的status是什么 如果是2，则不获取token；如果是除2以外的其他值，获取token
            Integer status = userTypeTokenService.getStatusByTranslationId(translationId);
            //如果status为空，插入一条userTypeToken表信息
            if (status == null) {
                Boolean b = userTypeTokenService.insertTokenInfo(request, translationId);
                status = userTypeTokenService.getStatusByTranslationId(translationId);
            }
            if (status != 2) {
                getUserTranslatedToken(request, translationId, userTypeTokenService, shopifyService);
            }
        } catch (Exception e) {
            appInsights.trackException(e);
            appInsights.trackTrace("startTokenCount " + request.getShopName() + "错误原因 errors ： " + e.getMessage());
        }
    }

    public static void getUserTranslatedToken(TranslateRequest request, Integer translationId, IUserTypeTokenService userTypeTokenService, ShopifyService shopifyService) {
        //将UserTypeToken的status修改为2
        userTypeTokenService.updateStatusByTranslationIdAndStatus(translationId, 2);
        ShopifyRequest shopifyRequest = convertTranslateRequestToShopifyRequest(request);
        //循环type获取token
        for (String key : TOKEN_MAP.keySet()
        ) {
            int tokens = 0;

            for (TranslateResourceDTO translateResourceDTO : TOKEN_MAP.get(key)) {
                int token = shopifyService.getTotalWords(shopifyRequest, "tokens", translateResourceDTO);
                tokens += token;
            }

            //将tokens存储到UserTypeToken对应的列里面
            userTypeTokenService.updateTokenByTranslationId(translationId, tokens, key);
            if ("collection".equals(key) || "notifications".equals(key) || "theme".equals(key)
                    || "article".equals(key) || "blog_titles".equals(key) || "filters".equals(key) || "metaobjects".equals(key)
                    || "pages".equals(key) || "products".equals(key) || "navigation".equals(key)
                    || "shop".equals(key) || "shipping".equals(key) || "delivery".equals(key) || "metadata".equals(key) || "policies".equals(key)) {
                UpdateWrapper<UserTypeTokenDO> updateWrapper = new UpdateWrapper<>();
                updateWrapper.eq("translation_id", translationId);

                // 根据传入的列名动态设置更新的字段
                updateWrapper.set(key, tokens);
                userTypeTokenService.update(null, updateWrapper);
            } else {
                appInsights.trackTrace("getUserTranslatedToken " + shopifyRequest.getShopName() + " Invalid column name");
            }
        }
        //token全部获取完之后修改，UserTypeToken的status==1
        userTypeTokenService.updateStatusByTranslationIdAndStatus(translationId, 1);
    }


    /**
     * 异步测试计数功能
     * */
    @Async
    public void testTokenCount(ShopifyRequest request, String key) {
        int tokens = 0;
        for (TranslateResourceDTO translateResourceDTO : TOKEN_MAP.get(key)) {
            int token = shopifyService.getTotalWords(request, "tokens", translateResourceDTO);
            tokens += token;
        }
        appInsights.trackTrace(request.getShopName() + " 用户 " + key + " 模块 消耗 tokens: " + tokens);
    }
}
