package com.bogdatech.repository.repo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.repository.entity.InitialTaskV2DO;
import com.bogdatech.repository.mapper.InitialTaskV2Mapper;
import com.bogdatech.utils.DbUtils;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class InitialTaskV2Repo extends ServiceImpl<InitialTaskV2Mapper, InitialTaskV2DO> {
    public List<InitialTaskV2DO> selectByLastDaysAndType(String type, Integer day) {
        return baseMapper.selectList(new LambdaQueryWrapper<InitialTaskV2DO>()
                .ge(InitialTaskV2DO::getCreatedAt, LocalDateTime.now().minusHours(24 * day))
                .eq(InitialTaskV2DO::getTaskType, type)
                .eq(InitialTaskV2DO::getIsDeleted, false));
    }

    public List<InitialTaskV2DO> selectByShopNameSource(String shopName, String source) {
        return baseMapper.selectList(new LambdaQueryWrapper<InitialTaskV2DO>()
                .eq(InitialTaskV2DO::getShopName, shopName)
                .eq(InitialTaskV2DO::getSource, source)
                .eq(InitialTaskV2DO::getIsDeleted, false));
    }

    public InitialTaskV2DO selectById(Integer taskId) {
        return baseMapper.selectOne(new LambdaQueryWrapper<InitialTaskV2DO>()
                .eq(InitialTaskV2DO::getId, taskId));
    }

    public List<InitialTaskV2DO> selectByShopName(String shopName) {
        return baseMapper.selectList(new LambdaQueryWrapper<InitialTaskV2DO>()
                .eq(InitialTaskV2DO::getShopName, shopName)
                .eq(InitialTaskV2DO::getIsDeleted, false));
    }

    public List<InitialTaskV2DO> selectByShopNameAndType(String shopName, String taskType) {
        return baseMapper.selectList(new LambdaQueryWrapper<InitialTaskV2DO>()
                .eq(InitialTaskV2DO::getShopName, shopName)
                .eq(InitialTaskV2DO::getTaskType, taskType)
                .eq(InitialTaskV2DO::getIsDeleted, false));
    }

    public List<InitialTaskV2DO> selectByStatus(int status) {
        return baseMapper.selectList(new LambdaQueryWrapper<InitialTaskV2DO>()
                .eq(InitialTaskV2DO::getStatus, status)
                .eq(InitialTaskV2DO::getIsDeleted, false));
    }

    public List<InitialTaskV2DO> selectByStatusAndTaskType(int status, String taskType) {
        return baseMapper.selectList(new LambdaQueryWrapper<InitialTaskV2DO>()
                .eq(InitialTaskV2DO::getStatus, status)
                .eq(InitialTaskV2DO::getTaskType, taskType)
                .eq(InitialTaskV2DO::getIsDeleted, false));
    }

    public List<InitialTaskV2DO> selectByTaskTypeAndNotEmail(String taskType) {
        return baseMapper.selectList(new LambdaQueryWrapper<InitialTaskV2DO>()
                .eq(InitialTaskV2DO::getTaskType, taskType)
                .eq(InitialTaskV2DO::isSendEmail, false)
                .eq(InitialTaskV2DO::getIsDeleted, false));
    }

    public List<InitialTaskV2DO> selectByStoppedAndNotEmail(String taskType) {
        return baseMapper.selectList(new LambdaQueryWrapper<InitialTaskV2DO>()
                .eq(InitialTaskV2DO::getStatus, 5)
                .eq(InitialTaskV2DO::isSendEmail, false)
                .eq(InitialTaskV2DO::getTaskType, taskType)
                .eq(InitialTaskV2DO::getIsDeleted, false));
    }

    public boolean insert(InitialTaskV2DO initialTaskV2DO) {
        DbUtils.setAllTime(initialTaskV2DO);
        return baseMapper.insert(initialTaskV2DO) > 0;
    }

    public boolean updateToStatus(InitialTaskV2DO initialTaskV2DO, int status) {
        initialTaskV2DO.setStatus(status);
        DbUtils.setUpdatedAt(initialTaskV2DO);
        return baseMapper.updateById(initialTaskV2DO) > 0;
    }

    public boolean updateById(InitialTaskV2DO initialTaskV2DO) {
        DbUtils.setUpdatedAt(initialTaskV2DO);
        return baseMapper.updateById(initialTaskV2DO) > 0;
    }

    public boolean deleteByShopNameSourceTarget(String shopName, String source, String target) {
        return baseMapper.update(new LambdaUpdateWrapper<InitialTaskV2DO>()
                .eq(InitialTaskV2DO::getShopName, shopName)
                .eq(InitialTaskV2DO::getSource, source)
                .eq(InitialTaskV2DO::getTarget, target)
                .eq(InitialTaskV2DO::getIsDeleted, false)
                .set(InitialTaskV2DO::getIsDeleted, true)
                .set(InitialTaskV2DO::getUpdatedAt, new Timestamp(System.currentTimeMillis()))
        ) > 0;
    }

    public boolean deleteByShopNameSourceAndType(String shopName, String source, String type) {
        return baseMapper.update(new LambdaUpdateWrapper<InitialTaskV2DO>()
                .eq(InitialTaskV2DO::getShopName, shopName)
                .eq(InitialTaskV2DO::getSource, source)
                .eq(InitialTaskV2DO::getIsDeleted, false)
                .eq(InitialTaskV2DO::getTaskType, type)
                .and(w -> w.eq(InitialTaskV2DO::getStatus, 4)
                        .or()
                        .eq(InitialTaskV2DO::getStatus, 5))
                .set(InitialTaskV2DO::getIsDeleted, true)
                .set(InitialTaskV2DO::getUpdatedAt, new Timestamp(System.currentTimeMillis()))
        ) > 0;
    }

    public List<InitialTaskV2DO> selectByShopNameSourceManual(String shopName, String source) {
        return baseMapper.selectList(new LambdaQueryWrapper<InitialTaskV2DO>()
                .eq(InitialTaskV2DO::getShopName, shopName)
                .eq(InitialTaskV2DO::getSource, source)
                .eq(InitialTaskV2DO::getIsDeleted, false)
                .eq(InitialTaskV2DO::getTaskType, "manual"));
    }
}
