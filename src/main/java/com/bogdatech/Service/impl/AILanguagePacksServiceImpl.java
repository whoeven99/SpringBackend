package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IAILanguagePacksService;
import com.bogdatech.entity.AILanguagePacksDO;
import com.bogdatech.mapper.AILanguagePacksMapper;
import com.bogdatech.model.controller.request.UserLanguageRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.bogdatech.enums.ErrorEnum.SQL_SELECT_ERROR;
import static com.bogdatech.enums.ErrorEnum.SQL_UPDATE_ERROR;

@Service
public class AILanguagePacksServiceImpl extends ServiceImpl<AILanguagePacksMapper, AILanguagePacksDO> implements IAILanguagePacksService {
    @Autowired
    private AILanguagePacksMapper aiLanguagePacksMapper;
    @Override
    public BaseResponse<Object> readAILanguagePacks() {
        AILanguagePacksDO[] aiLanguagePacksDOS = aiLanguagePacksMapper.readAILanguagePacks();
        if (aiLanguagePacksDOS != null) {
            return new BaseResponse<>().CreateSuccessResponse(aiLanguagePacksDOS);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_SELECT_ERROR);
    }

    @Override
    public BaseResponse<Object> addDefaultLanguagePack(String shopName) {
         if (baseMapper.addDefaultLanguagePack(shopName) > 0){
             return new BaseResponse<>().CreateSuccessResponse(200);
         }
        return new BaseResponse<>().CreateErrorResponse(SQL_SELECT_ERROR);
    }

    @Override
    public BaseResponse<Object> changeLanguagePack(UserLanguageRequest userLanguageRequest) {
        Integer i = baseMapper.changeLanguagePack(userLanguageRequest.getShopName(), userLanguageRequest.getPackId());
        if (i > 0) {
            return new BaseResponse<>().CreateSuccessResponse(200);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_UPDATE_ERROR);
    }

    @Override
    public String getPromotByPackId(Integer packId) {
        return baseMapper.getPromotByShopName(packId);
    }

    @Override
    public Integer getPackIdByShopName(String shopName) {
        return baseMapper.getPackIdByShopName(shopName);
    }
}
