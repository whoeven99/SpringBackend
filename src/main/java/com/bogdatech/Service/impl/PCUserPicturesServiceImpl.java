package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IPCUserPicturesService;
import com.bogdatech.entity.DO.PCUserPicturesDO;
import com.bogdatech.mapper.PCUserPicturesMapper;
import org.springframework.stereotype.Service;

@Service
public class PCUserPicturesServiceImpl extends ServiceImpl<PCUserPicturesMapper, PCUserPicturesDO> implements IPCUserPicturesService {
}
