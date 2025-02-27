package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IUsersService;
import com.bogdatech.entity.UsersDO;
import com.bogdatech.mapper.UsersMapper;
import com.bogdatech.model.controller.request.LoginAndUninstallRequest;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UsersMapper, UsersDO> implements IUsersService {

    @Override
    public int addUser(UsersDO request) {
        return baseMapper.insert(request);
    }

    @Override
    public UsersDO getUserByName(String shopName) {
        return baseMapper.getUserByName(shopName);
    }

    @Override
    public void unInstallApp(UsersDO userRequest) {
        baseMapper.unInstallApp(userRequest.getShopName());
    }

    @Override
    public void deleteUserGlossaryData(String shopName) {
        baseMapper.deleteUserGlossaryData(shopName);
    }

    @Override
    public void updateUserLoginTime(String shopName) {
        baseMapper.updateUserLoginTime(shopName);
    }

    @Override
    public LoginAndUninstallRequest getUserLoginTime(String shopName) {
        return baseMapper.getUserLoginTime(shopName);
    }

    @Override
    public void deleteCurrenciesData(String shopName) {
        baseMapper.deleteCurrenciesData(shopName);
    }

    @Override
    public void deleteTranslatesData(String shopName) {
        baseMapper.deleteTranslatesData(shopName);
    }

    @Override
    public void updateUserTokenByShopName(String shopName, String accessToken) {
        baseMapper.updateUserTokenByShopName(shopName, accessToken);
    }
}
