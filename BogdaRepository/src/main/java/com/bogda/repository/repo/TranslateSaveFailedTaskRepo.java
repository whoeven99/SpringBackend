package com.bogda.repository.repo;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.repository.entity.TranslateSaveFailedTaskDO;
import com.bogda.repository.mapper.TranslateSaveFailedTaskMapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;

@Service
public class TranslateSaveFailedTaskRepo extends ServiceImpl<TranslateSaveFailedTaskMapper, TranslateSaveFailedTaskDO> {

    public boolean insertFailedTask(Integer translateTaskId, Integer initialTaskId, String shopName, String errorMessage) {
        TranslateSaveFailedTaskDO failedTask = new TranslateSaveFailedTaskDO();
        failedTask.setTranslateTaskId(translateTaskId);
        failedTask.setInitialTaskId(initialTaskId);
        failedTask.setShopName(shopName);
        failedTask.setErrorMessage(errorMessage);
        failedTask.setRetryCount(0);
        failedTask.setRetried(false);
        failedTask.setIsDeleted(false);
        failedTask.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        failedTask.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        return baseMapper.insert(failedTask) > 0;
    }

    public TranslateSaveFailedTaskDO selectOneUnretried() {
        QueryWrapper<TranslateSaveFailedTaskDO> wrapper = new QueryWrapper<>();
        wrapper.select("TOP 1 *")
                .eq("retried", false)
                .eq("is_deleted", false)
                .orderByAsc("created_at");
        List<TranslateSaveFailedTaskDO> list = baseMapper.selectList(wrapper);
        return list.isEmpty() ? null : list.get(0);
    }

    public boolean markRetried(Integer id) {
        return baseMapper.update(new LambdaUpdateWrapper<TranslateSaveFailedTaskDO>()
                .eq(TranslateSaveFailedTaskDO::getId, id)
                .set(TranslateSaveFailedTaskDO::getRetried, true)
                .set(TranslateSaveFailedTaskDO::getUpdatedAt, new Timestamp(System.currentTimeMillis()))) > 0;
    }
}
