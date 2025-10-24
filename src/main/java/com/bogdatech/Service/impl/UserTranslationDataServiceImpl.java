package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IUserTranslationDataService;
import com.bogdatech.entity.DO.TranslateTasksDO;
import com.bogdatech.entity.DO.UserTranslationDataDO;
import com.bogdatech.mapper.UserTranslationDataMapper;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

import static com.bogdatech.enums.TranslateEnum.NOT_TRANSLATED;
import static com.bogdatech.enums.TranslateEnum.TRANSLATING;

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

    @Override
    public List<UserTranslationDataDO> selectWritingDataByShopNameAndSourceAndTarget(String shopName, String target) {
        String query = "\"target\":\"" + target + "\"";
        return baseMapper.selectList(new LambdaQueryWrapper<UserTranslationDataDO>().eq(UserTranslationDataDO::getShopName, shopName).eq(UserTranslationDataDO::getStatus, 0).like(UserTranslationDataDO::getPayload, query));
    }
}
