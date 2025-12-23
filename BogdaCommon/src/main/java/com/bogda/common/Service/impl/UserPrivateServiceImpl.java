package com.bogda.common.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.common.Service.IUserPrivateService;
import com.bogda.common.entity.DO.UserPrivateDO;
import com.bogda.common.mapper.UserPrivateMapper;
import org.springframework.stereotype.Service;

import static com.bogda.common.constants.TranslateConstants.SHOP_NAME;

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
