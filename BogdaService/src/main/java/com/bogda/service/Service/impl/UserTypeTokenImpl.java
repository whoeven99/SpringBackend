package com.bogda.service.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.service.Service.IUserTypeTokenService;
import com.bogda.common.entity.DO.UserTypeTokenDO;
import com.bogda.service.mapper.UserTypeTokenMapper;
import com.bogda.common.controller.request.TranslateRequest;
import org.springframework.stereotype.Service;

@Service
public class UserTypeTokenImpl extends ServiceImpl<UserTypeTokenMapper, UserTypeTokenDO> implements IUserTypeTokenService {

    @Override
    public void insertTypeInfo(TranslateRequest request, int translateId) {
        UserTypeTokenDO userTypeTokenDO = this.list(new LambdaQueryWrapper<UserTypeTokenDO>().eq(UserTypeTokenDO::getTranslationId, translateId).orderByAsc(UserTypeTokenDO::getId)).stream().findFirst().orElse(null);
        if (userTypeTokenDO == null) {
            baseMapper.insertTypeInfo(translateId);
        }
    }

    @Override
    public Boolean insertTokenInfo(TranslateRequest request, int translateId) {
        UserTypeTokenDO userTypeTokenDO = this.getOne(new QueryWrapper<UserTypeTokenDO>().eq("translation_id", translateId));
        if (userTypeTokenDO == null) {
            baseMapper.insertTypeInfo(translateId);
        }
        return true;
    }

    @Override
    public void insertInitial(String shopName) {
        baseMapper.insertTypeInfoByShopName(shopName);
    }

}
