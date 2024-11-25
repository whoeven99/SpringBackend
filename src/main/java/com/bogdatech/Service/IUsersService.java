package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.UsersDO;

public interface IUsersService extends IService<UsersDO> {
    int addUser(UsersDO request);
}
