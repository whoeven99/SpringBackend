package com.bogda.repository.repo;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.repository.entity.DeleteTasksDO;
import com.bogda.repository.mapper.DeleteTasksMapper;
import org.springframework.stereotype.Service;

@Service
public class DeleteTasksRepo extends ServiceImpl<DeleteTasksMapper, DeleteTasksDO> {
    public int deleteByInitialTaskId(Integer initialTaskId) {
        return baseMapper.deleteTopByInitialTaskId(initialTaskId);
    }
}
