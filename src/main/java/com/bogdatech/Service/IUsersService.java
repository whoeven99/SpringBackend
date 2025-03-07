package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.UsersDO;
import com.bogdatech.model.controller.request.LoginAndUninstallRequest;

public interface IUsersService extends IService<UsersDO> {
    int addUser(UsersDO request);
    UsersDO getUserByName(String shopName);

    void unInstallApp(UsersDO userRequest);

    void deleteUserGlossaryData(String shopName);

    void updateUserLoginTime(String shopName);

    LoginAndUninstallRequest getUserLoginTime(String shopName);

    void deleteCurrenciesData(String shopName);

    void deleteTranslatesData(String shopName);

    void updateUserTokenByShopName(String shopName, String accessToken);
}
