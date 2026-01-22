package com.bogda.service.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.service.Service.IAPGUserGeneratedTaskService;
import com.bogda.common.entity.DO.APGUserGeneratedTaskDO;
import com.bogda.service.mapper.APGUserGeneratedTaskMapper;
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

    @Override
    public APGUserGeneratedTaskDO getUserById(Long id) {
        return baseMapper.selectOne(new LambdaQueryWrapper<APGUserGeneratedTaskDO>().eq(APGUserGeneratedTaskDO::getUserId, id));
    }
}
