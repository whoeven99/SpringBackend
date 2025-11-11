package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IInitialTranslateTasksService;
import com.bogdatech.entity.DO.InitialTranslateTasksDO;
import com.bogdatech.enums.InitialTaskStatusEnum;
import com.bogdatech.mapper.InitialTranslateTasksMapper;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

import static com.bogdatech.logic.RabbitMqTranslateService.AUTO;
import static com.bogdatech.logic.RabbitMqTranslateService.MANUAL;

@Service
public class InitialTranslateTasksServiceImpl extends ServiceImpl<InitialTranslateTasksMapper, InitialTranslateTasksDO> implements IInitialTranslateTasksService {
    @Override
    public boolean updateStatusByTaskId(String taskId, int status) {
        return baseMapper.update(new LambdaUpdateWrapper<InitialTranslateTasksDO>()
                .eq(InitialTranslateTasksDO::getTaskId, taskId)
                .set(InitialTranslateTasksDO::getStatus, status)) > 0;
    }

    @Override
    public List<InitialTranslateTasksDO> selectTop10Tasks(int status, String manual) {
        return baseMapper.selectTop10Tasks(status, manual);
    }

    @Override
    public boolean updateAutoInitialDataByShopNameAndStatus(String shopName, int sourceStatus, List<String> targetList, int targetStatus, boolean isSendEmail, boolean isDeleted) {
        return baseMapper.update(new LambdaUpdateWrapper<InitialTranslateTasksDO>()
                .eq(InitialTranslateTasksDO::getShopName, shopName)
                .eq(InitialTranslateTasksDO::getStatus, sourceStatus)
                .eq(InitialTranslateTasksDO::getTaskType, AUTO)
                .in(InitialTranslateTasksDO::getTarget, targetList)
                .set(InitialTranslateTasksDO::getStatus, targetStatus)
                .set(InitialTranslateTasksDO::isSendEmail, isSendEmail)
                .set(InitialTranslateTasksDO::isDeleted, isDeleted)) > 0;
    }

    @Override
    public boolean deleteInitialTasksByShopNameAndSourceAndTargetAndTaskType(String shopName, String source, String manual) {
        return baseMapper.update(new LambdaUpdateWrapper<InitialTranslateTasksDO>()
                .eq(InitialTranslateTasksDO::getSource, source)
                .eq(InitialTranslateTasksDO::getShopName, shopName)
                .eq(InitialTranslateTasksDO::getTaskType, MANUAL)
                .eq(InitialTranslateTasksDO::isDeleted, false)
                .set(InitialTranslateTasksDO::isDeleted, true)) > 0;
    }

    @Override
    public List<InitialTranslateTasksDO> selectTasksByStatusAndNotSendEmail(List<Integer> statusList, boolean isSendEmail) {
        return baseMapper.selectList(new LambdaQueryWrapper<InitialTranslateTasksDO>()
                .in(InitialTranslateTasksDO::getStatus, statusList).eq(InitialTranslateTasksDO::isDeleted, false)
                .eq(InitialTranslateTasksDO::isSendEmail, isSendEmail));
    }

    @Override
    public boolean updateStatusAndSendEmailByTaskId(String taskId, int status, boolean isSendEmail) {
        return baseMapper.update(new LambdaUpdateWrapper<InitialTranslateTasksDO>().eq(InitialTranslateTasksDO::getTaskId, taskId)
                .set(InitialTranslateTasksDO::isSendEmail, isSendEmail).set(InitialTranslateTasksDO::getStatus, status)) > 0;
    }

    @Override
    public List<InitialTranslateTasksDO> selectTasksByShopNameAndIsDeleted(String shopName) {
        return baseMapper.selectList(new LambdaQueryWrapper<InitialTranslateTasksDO>()
                .eq(InitialTranslateTasksDO::getShopName, shopName)
                .eq(InitialTranslateTasksDO::isDeleted, false));
    }

    @Override
    public List<InitialTranslateTasksDO> selectTasksByShopNameAndIsDeletedOrderByCreatedAt(String shopName) {
        return baseMapper.selectList(new LambdaQueryWrapper<InitialTranslateTasksDO>()
                .eq(InitialTranslateTasksDO::getShopName, shopName).eq(InitialTranslateTasksDO::isDeleted, false)
                .orderByAsc(InitialTranslateTasksDO::getCreatedAt));
    }

    @Override
    public List<InitialTranslateTasksDO> selectTaskByStatusOrderByCreatedAt(int status) {
        return baseMapper.selectList(new LambdaQueryWrapper<InitialTranslateTasksDO>()
                .eq(InitialTranslateTasksDO::getStatus, status).orderByAsc(InitialTranslateTasksDO::getCreatedAt));
    }

}
