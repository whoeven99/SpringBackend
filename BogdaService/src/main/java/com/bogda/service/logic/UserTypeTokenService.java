package com.bogda.service.logic;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bogda.common.reporter.TraceReporterHolder;
import com.bogda.service.Service.IUserTypeTokenService;
import com.bogda.common.entity.DO.UserTypeTokenDO;
import com.bogda.common.controller.request.ShopifyRequest;
import com.bogda.common.controller.request.TranslateRequest;
import com.bogda.common.contants.TranslateConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;

import static com.bogda.common.entity.DO.TranslateResourceDTO.TOKEN_MAP;
import static com.bogda.service.utils.TypeConversionUtils.convertTranslateRequestToShopifyRequest;

@Component
@EnableAsync
public class UserTypeTokenService {
    @Autowired
    private IUserTypeTokenService userTypeTokenService;
    @Autowired
    private ShopifyService shopifyService;

    /**
     * 调用方法向数据库UserTypeToken的插入初始化数据，根据shopName向UserTypeToken表插入对应的数据
     *
     * @param request 用户数据对象，包含用户信息（如shopName、target、source, accessToken）
     */
    @Async
    public void getUserInitToken(TranslateRequest request) {
        UserTypeTokenDO userTypeTokenDO = userTypeTokenService.getOne(new QueryWrapper<UserTypeTokenDO>().eq(TranslateConstants.SHOP_NAME, request.getShopName()));
        if (userTypeTokenDO == null) {

            ShopifyRequest shopifyRequest = convertTranslateRequestToShopifyRequest(request);
            userTypeTokenService.insertInitial(shopifyRequest.getShopName());

            for (String key : TOKEN_MAP.keySet()
            ) {
                try {
                    shopifyService.insertInitialByTranslation(shopifyRequest, key, "initial");
                } catch (Exception e) {
                    TraceReporterHolder.report("UserTypeTokenService.getUserInitToken", "FatalException getUserInitToken " + shopifyRequest.getShopName() + " " + key + "模块获取失败： " + request);
                }
            }

        }
    }
}
