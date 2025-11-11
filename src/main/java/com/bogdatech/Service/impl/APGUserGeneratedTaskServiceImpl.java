package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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

    @Override
    public Boolean updateStatusTo2(Long id) {
        return baseMapper.updateStatusTo2(id) > 0;
    }

    @Override
    public APGUserGeneratedTaskDO getTaskByUserId(Long id) {
        return this.getOne(new LambdaQueryWrapper<APGUserGeneratedTaskDO>().eq(APGUserGeneratedTaskDO::getUserId, id));
    }

    @Override
    public Boolean updateTaskByUserId(APGUserGeneratedTaskDO apgUserGeneratedTaskDO, Long id) {
        return this.update(apgUserGeneratedTaskDO, new LambdaUpdateWrapper<APGUserGeneratedTaskDO>().eq(APGUserGeneratedTaskDO::getUserId, id));
    }
}
