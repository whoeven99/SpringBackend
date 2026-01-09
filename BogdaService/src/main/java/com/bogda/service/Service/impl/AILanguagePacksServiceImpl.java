package com.bogda.service.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.service.Service.IAILanguagePacksService;
import com.bogda.service.entity.DO.AILanguagePacksDO;
import com.bogda.service.mapper.AILanguagePacksMapper;
import org.springframework.stereotype.Service;

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
