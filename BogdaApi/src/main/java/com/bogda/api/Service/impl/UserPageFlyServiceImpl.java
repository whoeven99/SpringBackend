package com.bogda.api.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.api.Service.IUserPageFlyService;
import com.bogda.api.entity.DO.UserPageFlyDO;
import com.bogda.api.mapper.UserPageFlyMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserPageFlyServiceImpl extends ServiceImpl<UserPageFlyMapper, UserPageFlyDO> implements IUserPageFlyService{
    @Override
    public boolean insertUserPageFlysData(UserPageFlyDO userPageFly) {
        return baseMapper.insert(userPageFly) > 0;
    }

    @Override
    public UserPageFlyDO getUserPageFlysData(String languageCode, String shopName, String targetText, String sourceText) {
        return baseMapper.selectOne(new LambdaQueryWrapper<UserPageFlyDO>().eq(UserPageFlyDO::getLanguageCode, languageCode)
                .eq(UserPageFlyDO::getShopName, shopName).eq(UserPageFlyDO::getTargetText, targetText).eq(UserPageFlyDO::getSourceText, sourceText));
    }

    @Override
    public boolean updateUserPageFlysData(UserPageFlyDO userPageFly) {
        return baseMapper.updateById(userPageFly) > 0;
    }

    @Override
    public List<UserPageFlyDO> selectUserPageFlysData(String shopName, String languageCode) {
        return baseMapper.selectList(new LambdaQueryWrapper<UserPageFlyDO>().eq(UserPageFlyDO::getShopName, shopName)
                .eq(UserPageFlyDO::getLanguageCode, languageCode).eq(UserPageFlyDO::getIsDeleted, 0));
    }
}
