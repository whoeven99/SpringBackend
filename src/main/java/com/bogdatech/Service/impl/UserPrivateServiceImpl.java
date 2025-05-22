package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IUserPrivateService;
import com.bogdatech.entity.DO.UserPrivateDO;
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

    @Override
    public Integer addOrUpdateGoogleUserData(String shopName, String googleKey, Integer amount) {
        //先检测是否有数据，没有-存储，有-修改
        UserPrivateDO userPrivateDO = baseMapper.selectOne(new QueryWrapper<UserPrivateDO>()
                .eq(SHOP_NAME, shopName));
        if (userPrivateDO == null) {
            return baseMapper.saveGoogleUserData(shopName, googleKey, amount);
        }else {
            UserPrivateDO userPrivate = new UserPrivateDO();
            userPrivate.setAmount(amount);
            userPrivate.setGoogleKey(googleKey);
            return baseMapper.update(userPrivate, new QueryWrapper<UserPrivateDO>()
                    .eq(SHOP_NAME,shopName));
        }
    }

    @Override
    public Integer getUserId(String shopName) {
        return baseMapper.getUserIdByShopName(shopName);
    }

    @Override
    public Boolean updateAmountAndGoogleKey(String shopName) {
        return baseMapper.updateAmountAndGoogleKey(0, null, 0, shopName);
    }
}
