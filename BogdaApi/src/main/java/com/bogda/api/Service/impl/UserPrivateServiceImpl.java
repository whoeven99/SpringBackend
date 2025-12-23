package com.bogda.api.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.api.Service.IUserPrivateService;
import com.bogda.api.entity.DO.UserPrivateDO;
import com.bogda.api.mapper.UserPrivateMapper;
import org.springframework.stereotype.Service;

import static com.bogda.api.constants.TranslateConstants.SHOP_NAME;

@Service
public class UserPrivateServiceImpl extends ServiceImpl<UserPrivateMapper, UserPrivateDO> implements IUserPrivateService {
    @Override
    public UserPrivateDO selectOneByShopName(String shopName) {
        return baseMapper.selectOne(new QueryWrapper<UserPrivateDO>()
                .eq(SHOP_NAME, shopName));
    }

    @Override
    public Boolean updateAmountAndGoogleKey(String shopName) {
        return baseMapper.updateAmountAndGoogleKey(0, null, 0, shopName);
    }
}
