package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IInitialTranslateTasksService;
import com.bogdatech.entity.DO.InitialTranslateTasksDO;
import com.bogdatech.mapper.InitialTranslateTasksMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InitialTranslateTasksServiceImpl extends ServiceImpl<InitialTranslateTasksMapper, InitialTranslateTasksDO> implements IInitialTranslateTasksService {
    @Override
    public boolean updateStatusByTaskId(String taskId, int i) {
        return baseMapper.update(new LambdaUpdateWrapper<InitialTranslateTasksDO>()
                .eq(InitialTranslateTasksDO::getTaskId, taskId)
                .set(InitialTranslateTasksDO::getStatus, i)) > 0;
    }

    @Override
    public List<InitialTranslateTasksDO> selectTop10Tasks(int status, String type) {
        return baseMapper.selectTop10Tasks(status, type);
    }
}
