package com.bogda.repository.repo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.repository.entity.UserPrivateTranslateDO;
import com.bogda.repository.mapper.UserPrivateTranslateMapper;
import org.springframework.stereotype.Service;

@Service
public class UserPrivateTranslateRepo extends ServiceImpl<UserPrivateTranslateMapper, UserPrivateTranslateDO> {
    public UserPrivateTranslateDO getDataByShopNameAndApiName(String shopName, Integer apiName) {
        return baseMapper.selectOne(new LambdaQueryWrapper<UserPrivateTranslateDO>()
                .eq(UserPrivateTranslateDO::getShopName, shopName)
                .eq(UserPrivateTranslateDO::getApiName, apiName));
    }

    public Boolean updateDataByShopNameAndApiName(String shopName, Integer apiName, Long tokenLimit) {
        return baseMapper.update(new LambdaUpdateWrapper<UserPrivateTranslateDO>()
                .eq(UserPrivateTranslateDO::getShopName, shopName)
                .eq(UserPrivateTranslateDO::getApiName, apiName)
                .set(UserPrivateTranslateDO::getTokenLimit, tokenLimit)) > 0;
    }

}
