package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IUserPrivateService;
import com.bogdatech.entity.UserPrivateDO;
import com.bogdatech.mapper.UserPrivateMapper;
import org.springframework.stereotype.Service;

import static com.bogdatech.constants.TranslateConstants.SHOP_NAME;

@Service
public class UserPrivateServiceImpl extends ServiceImpl<UserPrivateMapper, UserPrivateDO> implements IUserPrivateService {

    @Override
    public UserPrivateDO selectOneByShopName(String shopName) {
        return baseMapper.selectOne(new QueryWrapper<UserPrivateDO>()
                .eq(SHOP_NAME, shopName));
    }
}
