package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IUsersService;
import com.bogdatech.entity.DO.UsersDO;
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
        return baseMapper.selectOne(new LambdaQueryWrapper<UsersDO>().eq(UsersDO::getShopName, shopName));
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

    @Override
    public boolean updateEncryptionEmailByShopName(String shopName, String encryptionEmail) {
        return baseMapper.update(new LambdaUpdateWrapper<UsersDO>()
                .eq(UsersDO::getShopName, shopName)
                .set(UsersDO::getEncryptionEmail, encryptionEmail)) > 0;
    }
}
