package com.bogda.service.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.common.reporter.TraceReporterHolder;
import com.bogda.service.Service.IAPGUserGeneratedSubtaskService;
import com.bogda.common.entity.DO.APGUserGeneratedSubtaskDO;
import com.bogda.service.mapper.APGUserGeneratedSubtaskMapper;
import org.springframework.stereotype.Service;



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
                    TraceReporterHolder.report("APGUserGeneratedSubtaskServiceImpl.updateStatusById", "FatalException updateStatusById 更新失败（返回false） errors ，准备第" + retryCount + "次重试，shopName=" + subtaskId);
                }
            } catch (Exception e) {
                retryCount++;
                TraceReporterHolder.report("APGUserGeneratedSubtaskServiceImpl.updateStatusById", "FatalException updateStatusById 更新失败（抛异常） errors ，准备第" + retryCount + "次重试，shopName=" + subtaskId + ", 错误=" + e);
            }

            try {
                Thread.sleep(retryDelayMillis);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        TraceReporterHolder.report("APGUserGeneratedSubtaskServiceImpl.updateStatusById", "FatalException updateStatusById 更新失败 errors ，重试" + maxRetries + "次后仍未成功，shopName=" + subtaskId);
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
