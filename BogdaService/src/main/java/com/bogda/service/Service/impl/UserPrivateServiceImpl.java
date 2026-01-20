package com.bogda.service.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.service.Service.IUserPrivateService;
import com.bogda.common.entity.DO.UserPrivateDO;
import com.bogda.service.mapper.UserPrivateMapper;
import com.bogda.common.contants.TranslateConstants;
import org.springframework.stereotype.Service;


@Service
public class UserPrivateServiceImpl extends ServiceImpl<UserPrivateMapper, UserPrivateDO> implements IUserPrivateService {
    @Override
    public UserPrivateDO selectOneByShopName(String shopName) {
        return baseMapper.selectOne(new QueryWrapper<UserPrivateDO>()
                .eq(TranslateConstants.SHOP_NAME, shopName));
    }

    @Override
    public Boolean updateAmountAndGoogleKey(String shopName) {
        return baseMapper.updateAmountAndGoogleKey(0, null, 0, shopName);
    }
}
