package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IUserTrialsService;
import com.bogdatech.entity.DO.UserTrialsDO;
import com.bogdatech.mapper.UserTrialsMapper;
import org.springframework.stereotype.Service;

@Service
public class UserTrialsServiceImpl extends ServiceImpl<UserTrialsMapper, UserTrialsDO> implements IUserTrialsService {
}
