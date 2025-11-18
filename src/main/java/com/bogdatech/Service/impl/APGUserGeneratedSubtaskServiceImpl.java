package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IAPGUserGeneratedSubtaskService;
import com.bogdatech.entity.DO.APGUserGeneratedSubtaskDO;
import com.bogdatech.mapper.APGUserGeneratedSubtaskMapper;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@Service
public class APGUserGeneratedSubtaskServiceImpl extends ServiceImpl<APGUserGeneratedSubtaskMapper, APGUserGeneratedSubtaskDO> implements IAPGUserGeneratedSubtaskService {
    @Override
    public Boolean updateStatusById(String subtaskId, int i) {
        final int maxRetries = 3;
        final long retryDelayMillis = 500;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            try {
                Boolean b = baseMapper.updateStatusById(subtaskId, i);
                if (Boolean.TRUE.equals(b)) {
                    return true;
                } else {
                    retryCount++;
                    appInsights.trackTrace("updateStatusById 更新失败（返回false） errors ，准备第" + retryCount + "次重试，shopName=" + subtaskId);
                }
            } catch (Exception e) {
                retryCount++;
                appInsights.trackTrace("updateStatusById 更新失败（抛异常） errors ，准备第" + retryCount + "次重试，shopName=" + subtaskId + ", 错误=" + e);
            }

            try {
                Thread.sleep(retryDelayMillis);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        appInsights.trackTrace("updateStatusById 更新失败 errors ，重试" + maxRetries + "次后仍未成功，shopName=" + subtaskId);
        return false;
    }

    @Override
    public Boolean updateAllStatusByUserId(Long id, int i) {
        return baseMapper.updateAllStatusByUserId(id, i);
    }

    @Override
    public Boolean update34StatusTo9(Long id) {
        return baseMapper.update34StatusTo9(id);
    }

    @Override
    public List<APGUserGeneratedSubtaskDO> getUnfinishedByStatusAndUserId(List<Integer> statusList, Long userId) {
        return baseMapper.selectList(new LambdaQueryWrapper<APGUserGeneratedSubtaskDO>()
                .in(APGUserGeneratedSubtaskDO::getStatus, statusList)
                .eq(APGUserGeneratedSubtaskDO::getUserId, userId));
    }

    @Override
    public List<APGUserGeneratedSubtaskDO> selectTasksByStatusOrderByCreateTime(int status) {
        return baseMapper.selectList(new LambdaQueryWrapper<APGUserGeneratedSubtaskDO>().eq(APGUserGeneratedSubtaskDO::getStatus, 0)
                .orderBy(true, true, APGUserGeneratedSubtaskDO::getCreateTime));
    }

    @Override
    public List<APGUserGeneratedSubtaskDO> selectTasksByUserIdAndStatus(Long userId, int status) {
        return baseMapper.selectList(new LambdaQueryWrapper<APGUserGeneratedSubtaskDO>().eq(APGUserGeneratedSubtaskDO::getUserId, userId)
                .eq(APGUserGeneratedSubtaskDO::getStatus, status));
    }

    @Override
    public List<APGUserGeneratedSubtaskDO> selectTask10ToGenerate() {
        return baseMapper.selectTask10ToGenerate();
    }
}
