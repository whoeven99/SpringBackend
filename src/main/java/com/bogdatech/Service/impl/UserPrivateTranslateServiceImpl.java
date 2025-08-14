package com.bogdatech.Service.impl;

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
}
