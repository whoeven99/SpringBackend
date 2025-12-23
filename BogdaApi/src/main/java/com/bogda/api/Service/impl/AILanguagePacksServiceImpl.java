package com.bogda.api.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.api.Service.IAILanguagePacksService;
import com.bogda.api.entity.DO.AILanguagePacksDO;
import com.bogda.api.mapper.AILanguagePacksMapper;
import com.bogda.api.model.controller.request.UserLanguageRequest;
import com.bogda.api.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.bogda.api.enums.ErrorEnum.SQL_SELECT_ERROR;
import static com.bogda.api.enums.ErrorEnum.SQL_UPDATE_ERROR;

@Service
public class AILanguagePacksServiceImpl extends ServiceImpl<AILanguagePacksMapper, AILanguagePacksDO> implements IAILanguagePacksService {
    @Override
    public void addDefaultLanguagePack(String shopName) {
        //先判断数据库里是否有数据 没有就添加 有就跳过
        if (baseMapper.getPackIdByShopName(shopName) == null){
            Integer id = baseMapper.getPackIdByPackName("General");
            baseMapper.addDefaultLanguagePack(shopName, id);

        }
    }

    @Override
    public Integer getPackIdByShopName(String shopName) {
        return baseMapper.getPackIdByShopName(shopName);
    }
}
