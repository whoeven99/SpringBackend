package com.bogda.common.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.common.Service.IUserPrivateTranslateService;
import com.bogda.common.entity.DO.UserPrivateTranslateDO;
import com.bogda.common.mapper.UserPrivateTranslateMapper;
import org.springframework.stereotype.Service;

@Service
public class UserPrivateTranslateServiceImpl extends ServiceImpl<UserPrivateTranslateMapper, UserPrivateTranslateDO> implements IUserPrivateTranslateService {
    @Override
    public Boolean updateUserUsedCount(Integer apiName, int length, String shopName, Long limit) {
        return baseMapper.updateUserUsedCount(length, shopName, apiName, limit);
    }
}
