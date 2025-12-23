package com.bogda.common.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.common.Service.IUsersService;
import com.bogda.common.entity.DO.UsersDO;
import com.bogda.common.mapper.UsersMapper;
import com.bogda.common.model.controller.request.LoginAndUninstallRequest;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UsersMapper, UsersDO> implements IUsersService {

    @Override
    public int addUser(UsersDO request) {
        return baseMapper.insert(request);
    }

    @Override
    public UsersDO getUserByName(String shopName) {
        return baseMapper.selectOne(new LambdaQueryWrapper<UsersDO>().eq(UsersDO::getShopName, shopName));
    }

    @Override
    public void unInstallApp(UsersDO userRequest) {
        baseMapper.unInstallApp(userRequest.getShopName());
    }

    @Override
    public void updateUserLoginTime(String shopName) {
        baseMapper.updateUserLoginTime(shopName);
    }

    @Override
    public void updateUserTokenByShopName(String shopName, String accessToken) {
        baseMapper.updateUserTokenByShopName(shopName, accessToken);
    }
}
