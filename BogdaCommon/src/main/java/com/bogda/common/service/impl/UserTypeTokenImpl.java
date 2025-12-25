package com.bogda.common.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.common.service.IUserTypeTokenService;
import com.bogda.common.entity.DO.UserTypeTokenDO;
import com.bogda.common.mapper.UserTypeTokenMapper;
import com.bogda.common.model.controller.request.TranslateRequest;
import org.springframework.stereotype.Service;

@Service
public class UserTypeTokenImpl extends ServiceImpl<UserTypeTokenMapper, UserTypeTokenDO> implements IUserTypeTokenService {

    @Override
    public void insertTypeInfo(TranslateRequest request, int translateId) {
        //先根据translateId查询UserTypeToken是否存在，如果存在就返回，如果不存在就插入一条数据到数据库
        UserTypeTokenDO userTypeTokenDO = this.list(new LambdaQueryWrapper<UserTypeTokenDO>().eq(UserTypeTokenDO::getTranslationId, translateId).orderByAsc(UserTypeTokenDO::getId)).stream().findFirst().orElse(null);
        if (userTypeTokenDO == null) {
            baseMapper.insertTypeInfo(translateId);
        }
    }

    @Override
    public Boolean insertTokenInfo(TranslateRequest request, int translateId) {
        //先根据translateId查询UserTypeToken是否存在，如果存在就返回，如果不存在就插入一条数据到数据库
        UserTypeTokenDO userTypeTokenDO = this.getOne(new QueryWrapper<UserTypeTokenDO>().eq("translation_id", translateId));
        if (userTypeTokenDO == null) {
            baseMapper.insertTypeInfo(translateId);
        }
        return true;
    }

    @Override
    public Integer getStatusByTranslationId(int translationId) {
        return baseMapper.getStatusByTranslationId(translationId);
    }

    @Override
    public void updateTokenByTranslationId(int translationId, int tokens, String key) {
        baseMapper.updateTokenByTranslationId(translationId, tokens, key);
    }

    @Override
    public void updateStatusByTranslationIdAndStatus(int translationId, int i) {
        baseMapper.updateStatusByTranslationIdAndStatus(translationId,i);
    }

    @Override
    public void insertInitial(String shopName) {
        baseMapper.insertTypeInfoByShopName(shopName);
    }

}
