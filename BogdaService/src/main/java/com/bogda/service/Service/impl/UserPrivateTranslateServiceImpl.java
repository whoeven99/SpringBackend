package com.bogda.service.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.service.Service.IUserPrivateTranslateService;
import com.bogda.common.entity.DO.UserPrivateTranslateDO;
import com.bogda.service.mapper.UserPrivateTranslateMapper;
import org.springframework.stereotype.Service;

@Service
public class UserPrivateTranslateServiceImpl extends ServiceImpl<UserPrivateTranslateMapper, UserPrivateTranslateDO> implements IUserPrivateTranslateService {
}
