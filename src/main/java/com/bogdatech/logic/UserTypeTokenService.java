package com.bogdatech.logic;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.Service.IUserTypeTokenService;
import com.bogdatech.entity.UserTypeTokenDO;
import com.bogdatech.model.controller.request.TranslateRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UserTypeTokenService {
    private final IUserTypeTokenService userTypeTokenService;
    private final ITranslatesService translatesService;
    @Autowired
    public UserTypeTokenService(IUserTypeTokenService userTypeTokenService, ITranslatesService translatesService) {
        this.userTypeTokenService = userTypeTokenService;
        this.translatesService = translatesService;
    }

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


}
