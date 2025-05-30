package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IAILanguagePacksService;
import com.bogdatech.entity.DO.AILanguagePacksDO;
import com.bogdatech.mapper.AILanguagePacksMapper;
import com.bogdatech.model.controller.request.UserLanguageRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.bogdatech.enums.ErrorEnum.SQL_SELECT_ERROR;
import static com.bogdatech.enums.ErrorEnum.SQL_UPDATE_ERROR;

@Service
public class AILanguagePacksServiceImpl extends ServiceImpl<AILanguagePacksMapper, AILanguagePacksDO> implements IAILanguagePacksService {

    private final AILanguagePacksMapper aiLanguagePacksMapper;

    @Autowired
    public AILanguagePacksServiceImpl(AILanguagePacksMapper aiLanguagePacksMapper) {
        this.aiLanguagePacksMapper = aiLanguagePacksMapper;
    }

    @Override
    public BaseResponse<Object> readAILanguagePacks() {
        AILanguagePacksDO[] aiLanguagePacksDOS = aiLanguagePacksMapper.readAILanguagePacks();
        if (aiLanguagePacksDOS != null) {
            return new BaseResponse<>().CreateSuccessResponse(aiLanguagePacksDOS);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_SELECT_ERROR);
    }

    @Override
    public void addDefaultLanguagePack(String shopName) {
        //先判断数据库里是否有数据 没有就添加 有就跳过
        if (baseMapper.getPackIdByShopName(shopName) == null){
            Integer id = baseMapper.getPackIdByPackName("General");
            baseMapper.addDefaultLanguagePack(shopName, id);

        }
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
    public AILanguagePacksDO getPromotByPackId(Integer packId) {
        return baseMapper.getPackByShopName(packId);
    }

    @Override
    public Integer getPackIdByShopName(String shopName) {
        return baseMapper.getPackIdByShopName(shopName);
    }

    @Override
    public String getLanguagePackByShopName(String shopName) {
        return baseMapper.getLanguagePackByShopName(shopName);
    }

    @Override
    public Boolean insertOrUpdateCategory(String shopName, String categoryText) {
        //先判断数据库里是否有数据 没有就添加 有就更新
        Boolean flag;
        if(baseMapper.getAlLanguageByShopName(shopName) == null){
            flag = baseMapper.insertUserAlLanguagePacks(shopName, categoryText, 4);
        }else {
            flag = baseMapper.updateUserAlLanguagePacks(shopName, categoryText);
        }
        return flag;
    }
}
