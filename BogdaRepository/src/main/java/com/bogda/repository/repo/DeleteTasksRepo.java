package com.bogda.repository.repo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.repository.entity.DeleteTasksDO;
import com.bogda.repository.mapper.DeleteTasksMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DeleteTasksRepo extends ServiceImpl<DeleteTasksMapper, DeleteTasksDO> {
    public boolean saveSingleData(Integer InitialTaskId, String resourceId, String key) {
        return baseMapper.insert(new DeleteTasksDO(InitialTaskId, resourceId, key, false)) > 0;
    }

    public DeleteTasksDO selectOneByInitialTaskIdAndNotDeleted(Integer initialTaskId) {
        return baseMapper.selectOne(new LambdaQueryWrapper<DeleteTasksDO>().eq(DeleteTasksDO::getInitialTaskId, initialTaskId)
                .eq(DeleteTasksDO::getDeletedToShopify, false)
                .eq(DeleteTasksDO::getIsDeleted, false));
    }

    public List<DeleteTasksDO> selectByInitialTaskIdAndResourceIdWithLimit(Integer initialTaskId, String resourceId) {
        return baseMapper.selectList(new LambdaQueryWrapper<DeleteTasksDO>().eq(DeleteTasksDO::getInitialTaskId, initialTaskId)
                .eq(DeleteTasksDO::getResourceId, resourceId)
                .eq(DeleteTasksDO::getDeletedToShopify, false)
                .eq(DeleteTasksDO::getIsDeleted, false));
    }

    public boolean updateDeletedToShopify(Integer id) {
        return baseMapper.update(new LambdaUpdateWrapper<DeleteTasksDO>().eq(DeleteTasksDO::getId, id)
                .set(DeleteTasksDO::getIsDeleted, true)
                .set(DeleteTasksDO::getDeletedToShopify, true)) > 0;
    }
}
