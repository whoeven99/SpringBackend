package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IAPGUsersService;
import com.bogdatech.entity.DO.APGUsersDO;
import com.bogdatech.mapper.APGUsersMapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@Service
public class APGUsersServiceImpl extends ServiceImpl<APGUsersMapper, APGUsersDO> implements IAPGUsersService {
}
