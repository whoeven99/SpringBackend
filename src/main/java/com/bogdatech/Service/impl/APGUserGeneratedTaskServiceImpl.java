package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IAPGUserGeneratedTaskService;
import com.bogdatech.entity.DO.APGUserGeneratedTaskDO;
import com.bogdatech.mapper.APGUserGeneratedTaskMapper;
import org.springframework.stereotype.Service;

@Service
public class APGUserGeneratedTaskServiceImpl extends ServiceImpl<APGUserGeneratedTaskMapper, APGUserGeneratedTaskDO> implements IAPGUserGeneratedTaskService {

    @Override
    public Boolean updateStatusByUserId(Long userId, int i) {
        return baseMapper.updateStatusByUserId(userId, i) > 0;
    }
}
