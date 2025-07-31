package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IUserTranslationDataService;
import com.bogdatech.entity.DO.UserTranslationDataDO;
import com.bogdatech.mapper.UserTranslationDataMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserTranslationDataServiceImpl extends ServiceImpl<UserTranslationDataMapper, UserTranslationDataDO> implements IUserTranslationDataService {

    @Override
    public Boolean insertTranslationData(String translationData, String shopName) {
        UserTranslationDataDO userTranslationDataDO = new UserTranslationDataDO(null, 0, translationData, shopName);
        return baseMapper.insert(userTranslationDataDO) > 0;
    }

    @Override
    public List<UserTranslationDataDO> selectTranslationDataList() {
        return baseMapper.selectTranslationDataList();
    }
}
