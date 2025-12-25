package com.bogda.common.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.common.service.IUserTranslationDataService;
import com.bogda.common.entity.DO.UserTranslationDataDO;
import com.bogda.common.mapper.UserTranslationDataMapper;
import org.springframework.stereotype.Service;

@Service
public class UserTranslationDataServiceImpl extends ServiceImpl<UserTranslationDataMapper, UserTranslationDataDO> implements IUserTranslationDataService {

    @Override
    public Boolean insertTranslationData(String translationData, String shopName) {
        UserTranslationDataDO userTranslationDataDO = new UserTranslationDataDO(null, 0, translationData, shopName);
        return baseMapper.insert(userTranslationDataDO) > 0;
    }
}
