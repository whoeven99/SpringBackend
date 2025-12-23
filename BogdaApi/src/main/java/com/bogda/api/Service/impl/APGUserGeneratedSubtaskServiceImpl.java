package com.bogda.api.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.api.Service.IAPGUserGeneratedSubtaskService;
import com.bogda.api.entity.DO.APGUserGeneratedSubtaskDO;
import com.bogda.api.mapper.APGUserGeneratedSubtaskMapper;
import org.springframework.stereotype.Service;

import static com.bogda.api.utils.CaseSensitiveUtils.appInsights;

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
}
