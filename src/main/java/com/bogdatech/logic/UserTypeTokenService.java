package com.bogdatech.logic;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.Service.IUserTypeTokenService;
import com.bogdatech.entity.UserTypeTokenDO;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.bogdatech.entity.TranslateResourceDTO.TOKEN_MAP;
import static com.bogdatech.utils.TypeConversionUtils.convertTranslateRequestToShopifyRequest;

@Component
public class UserTypeTokenService {
    private final IUserTypeTokenService userTypeTokenService;
    private final ITranslatesService translatesService;
    private final TranslateService translateService;

    @Autowired
    public UserTypeTokenService(IUserTypeTokenService userTypeTokenService, ITranslatesService translatesService, TranslateService translateService) {
        this.userTypeTokenService = userTypeTokenService;
        this.translatesService = translatesService;
        this.translateService = translateService;
    }
    private static TelemetryClient appInsights = new TelemetryClient();

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


    public void getUserInitToken(TranslateRequest request) {
        UserTypeTokenDO userTypeTokenDO = userTypeTokenService.getOne(new QueryWrapper<UserTypeTokenDO>().eq("shop_name", request.getShopName()));
        if (userTypeTokenDO == null){

            ShopifyRequest shopifyRequest = convertTranslateRequestToShopifyRequest(request);
            //将shopName初始值存储到数据库中
            userTypeTokenService.insertInitial(shopifyRequest.getShopName());

            //循环type获取token
            for (String key : TOKEN_MAP.keySet()
            ) {
                try {
                    translateService.insertInitialByTranslation(shopifyRequest, key, "initial");
                } catch (Exception e) {
                    appInsights.trackTrace(key + "模块获取失败： " + request);
                }
            }

        }
    }

    //根据shopName获取该用户初始值
    public UserTypeTokenDO getUserInitTokenByShopName(String shopName) {
        return userTypeTokenService.getOne(new QueryWrapper<UserTypeTokenDO>().eq("shop_name", shopName));
    }
}
