package com.bogda.service.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.service.entity.DO.UsersDO;

public interface IUsersService extends IService<UsersDO> {
    int addUser(UsersDO request);
    UsersDO getUserByName(String shopName);

    void unInstallApp(UsersDO userRequest);

    void updateUserLoginTime(String shopName);

    void updateUserTokenByShopName(String shopName, String accessToken);
}
