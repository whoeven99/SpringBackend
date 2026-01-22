package com.bogda.repository.repo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.repository.entity.InitialTaskV2DO;
import com.bogda.repository.mapper.InitialTaskV2Mapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class InitialTaskV2Repo extends ServiceImpl<InitialTaskV2Mapper, InitialTaskV2DO> {
    public List<InitialTaskV2DO> selectTaskBeforeDaysAndDeleted(Integer day) {
        return baseMapper.selectList(new LambdaQueryWrapper<InitialTaskV2DO>()
                .le(InitialTaskV2DO::getCreatedAt, LocalDateTime.now().minusHours(24 * day))
                .eq(InitialTaskV2DO::getIsDeleted, true));
    }

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
                .eq(InitialTaskV2DO::getIsDeleted, false)
                .orderByDesc(InitialTaskV2DO::getCreatedAt));
    }

    public InitialTaskV2DO selectById(Integer taskId) {
        return baseMapper.selectOne(new LambdaQueryWrapper<InitialTaskV2DO>()
                .eq(InitialTaskV2DO::getId, taskId));
    }

    public List<InitialTaskV2DO> selectStoppedByShopName(String shopName) {
        return baseMapper.selectList(new LambdaQueryWrapper<InitialTaskV2DO>()
                .eq(InitialTaskV2DO::getShopName, shopName)
                .eq(InitialTaskV2DO::getStatus, 5)
                .eq(InitialTaskV2DO::getIsDeleted, false));
    }

    public List<InitialTaskV2DO> selectByShopNameAndType(String shopName, String taskType) {
        return baseMapper.selectList(new LambdaQueryWrapper<InitialTaskV2DO>()
                .eq(InitialTaskV2DO::getShopName, shopName)
                .eq(InitialTaskV2DO::getTaskType, taskType)
                .eq(InitialTaskV2DO::isSendEmail, false)
                .eq(InitialTaskV2DO::getIsDeleted, false));
    }

    public List<InitialTaskV2DO> selectByStoppedAndNotSaveLastDay() {
        return baseMapper.selectList(new LambdaQueryWrapper<InitialTaskV2DO>()
                .eq(InitialTaskV2DO::getStatus, 5)
                .eq(InitialTaskV2DO::isSaveStatus, false)
                .ge(InitialTaskV2DO::getCreatedAt, LocalDateTime.now().minusHours(48))
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
                .in(InitialTaskV2DO::getStatus, 3, 5)
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

    public List<InitialTaskV2DO> selectByShopNameStoppedAndNotEmail(String shopName, String taskType) {
        return baseMapper.selectList(new LambdaQueryWrapper<InitialTaskV2DO>()
                .eq(InitialTaskV2DO::getShopName, shopName)
                .eq(InitialTaskV2DO::getStatus, 5)
                .eq(InitialTaskV2DO::isSendEmail, false)
                .eq(InitialTaskV2DO::getTaskType, taskType)
                .eq(InitialTaskV2DO::getIsDeleted, false));
    }

    public boolean insert(InitialTaskV2DO initialTaskV2DO) {
        initialTaskV2DO.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        return baseMapper.insert(initialTaskV2DO) > 0;
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

    public boolean deleteById(Integer id) {
        return baseMapper.deleteById(id) > 0;
    }

    public boolean updateSendEmailById(Integer id, boolean sendEmail) {
        return baseMapper.update(new LambdaUpdateWrapper<InitialTaskV2DO>()
                .set(InitialTaskV2DO::isSendEmail, sendEmail)
                .set(InitialTaskV2DO::getUpdatedAt, new Timestamp(System.currentTimeMillis()))
                .eq(InitialTaskV2DO::getId, id)) > 0;
    }

    public boolean updateSendEmailAndStatusById(boolean sendEmail, int status, Integer id) {
        return baseMapper.update(new LambdaUpdateWrapper<InitialTaskV2DO>()
                .set(InitialTaskV2DO::isSendEmail, sendEmail)
                .set(InitialTaskV2DO::getStatus, status)
                .set(InitialTaskV2DO::getUpdatedAt, new Timestamp(System.currentTimeMillis()))
                .eq(InitialTaskV2DO::getId, id)) > 0;
    }

    public boolean updateStatusAndInitMinutes(int status, int initTimeInMinutes, Integer id) {
        return baseMapper.update(new LambdaUpdateWrapper<InitialTaskV2DO>().set(InitialTaskV2DO::getStatus, status)
                .set(InitialTaskV2DO::getInitMinutes, initTimeInMinutes)
                .set(InitialTaskV2DO::getUpdatedAt, new Timestamp(System.currentTimeMillis()))
                .eq(InitialTaskV2DO::getId, id)) > 0;
    }

    public boolean updateStatusUsedTokenTranslationMinutesModuleById(int status, Integer usedToken,
                                                                     int translationTimeInMinutes, String transModelType,
                                                                     int initialId) {
        return baseMapper.update(new LambdaUpdateWrapper<InitialTaskV2DO>().set(InitialTaskV2DO::getStatus, status)
                .set(InitialTaskV2DO::getUsedToken, usedToken)
                .set(InitialTaskV2DO::getTranslationMinutes, translationTimeInMinutes)
                .set(InitialTaskV2DO::getTransModelType, transModelType)
                .set(InitialTaskV2DO::getUpdatedAt, new Timestamp(System.currentTimeMillis()))
                .eq(InitialTaskV2DO::getId, initialId)) > 0;

    }

    public boolean updateStatusAndSendEmailById(Integer status, Integer id, boolean isSendEmail, boolean saveStatus) {
        return baseMapper.update(new LambdaUpdateWrapper<InitialTaskV2DO>().set(InitialTaskV2DO::getStatus, status)
                .set(InitialTaskV2DO::isSendEmail, isSendEmail)
                .set(InitialTaskV2DO::isSaveStatus, saveStatus)
                .set(InitialTaskV2DO::getUpdatedAt, new Timestamp(System.currentTimeMillis()))
                .eq(InitialTaskV2DO::getId, id)) > 0;
    }

    public boolean updateStatusSavingShopifyMinutesById(Integer status, Integer savingShopifyMinutes, Integer id) {
        return baseMapper.update(new LambdaUpdateWrapper<InitialTaskV2DO>().set(InitialTaskV2DO::getStatus, status)
                .set(InitialTaskV2DO::getSavingShopifyMinutes, savingShopifyMinutes)
                .set(InitialTaskV2DO::isSaveStatus, true)
                .set(InitialTaskV2DO::getUpdatedAt, new Timestamp(System.currentTimeMillis()))
                .eq(InitialTaskV2DO::getId, id)) > 0;
    }

    public List<InitialTaskV2DO> selectByShopNameAndNotDeleted(String shopName) {
        return baseMapper.selectList(new LambdaQueryWrapper<InitialTaskV2DO>()
                .eq(InitialTaskV2DO::getShopName, shopName)
                .eq(InitialTaskV2DO::getIsDeleted, false));
    }
}
