package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IUserPrivateTranslateService;
import com.bogdatech.entity.DO.UserPrivateTranslateDO;
import com.bogdatech.mapper.UserPrivateTranslateMapper;
import org.springframework.stereotype.Service;

@Service
public class UserPrivateTranslateServiceImpl extends ServiceImpl<UserPrivateTranslateMapper, UserPrivateTranslateDO> implements IUserPrivateTranslateService {
    @Override
    public Boolean updateUserUsedCount(Integer apiName, int length, String shopName, Long limit) {
        return baseMapper.updateUserUsedCount(length, shopName, apiName, limit);
    }

    @Override
    public UserPrivateTranslateDO getPrivateDataByShopNameAndUserKey(String shopName, String userKey) {
        return baseMapper.selectOne(new LambdaQueryWrapper<UserPrivateTranslateDO>()
                .eq(UserPrivateTranslateDO::getShopName, shopName).eq(UserPrivateTranslateDO::getApiKey, userKey));
    }

    @Override
    public UserPrivateTranslateDO getPrivateDataByShopNameAndApiName(String shopName, Integer apiName) {
        return baseMapper.selectOne(new LambdaQueryWrapper<UserPrivateTranslateDO>()
                .eq(UserPrivateTranslateDO::getShopName, shopName).eq(UserPrivateTranslateDO::getApiName, apiName));
    }

    @Override
    public Boolean updateUserDataByShopNameAndApiName(String shopName, Integer apiName, String apiModel, String promptWord, Long tokenLimit, Boolean isSelected) {
        return baseMapper.update(new LambdaUpdateWrapper<UserPrivateTranslateDO>()
                .eq(UserPrivateTranslateDO::getShopName, shopName)
                .eq(UserPrivateTranslateDO::getApiName, apiName)
                .set(UserPrivateTranslateDO::getApiModel, apiModel)
                .set(UserPrivateTranslateDO::getPromptWord, promptWord)
                .set(UserPrivateTranslateDO::getTokenLimit, tokenLimit)
                .set(UserPrivateTranslateDO::getIsSelected, isSelected)) > 0;
    }
}
