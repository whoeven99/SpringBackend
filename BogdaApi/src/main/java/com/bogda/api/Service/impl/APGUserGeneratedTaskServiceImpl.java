package com.bogda.api.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.api.Service.IAPGUserGeneratedTaskService;
import com.bogda.api.entity.DO.APGUserGeneratedTaskDO;
import com.bogda.api.mapper.APGUserGeneratedTaskMapper;
import org.springframework.stereotype.Service;

@Service
public class APGUserGeneratedTaskServiceImpl extends ServiceImpl<APGUserGeneratedTaskMapper, APGUserGeneratedTaskDO> implements IAPGUserGeneratedTaskService {

    @Override
    public Boolean updateStatusByUserId(Long userId, int i) {
        return baseMapper.updateStatusByUserId(userId, i) > 0;
    }

    @Override
    public Boolean updateStatusTo2(Long id) {
        return baseMapper.updateStatusTo2(id) > 0;
    }
}
