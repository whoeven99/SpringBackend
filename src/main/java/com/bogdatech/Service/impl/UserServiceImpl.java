package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IUsersService;
import com.bogdatech.entity.UsersDO;
import com.bogdatech.mapper.UsersMapper;
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
}
