package com.bogda.service.Service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.common.reporter.ExceptionReporterHolder;
import com.bogda.common.reporter.TraceReporterHolder;
import com.bogda.service.Service.ITranslatesService;
import com.bogda.common.entity.DO.TranslatesDO;
import com.bogda.service.mapper.TranslatesMapper;
import com.bogda.common.controller.request.TranslateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TranslatesServiceImpl extends ServiceImpl<TranslatesMapper, TranslatesDO> implements ITranslatesService {

    @Override
    public Integer readStatus(TranslateRequest request) {
        return baseMapper.getStatusInTranslates(request.getShopName(), request.getTarget(), request.getSource());
    }

    @Override
    public Integer insertShopTranslateInfo(TranslateRequest request, int status) {
        return baseMapper.insertShopTranslateInfo(request.getSource(), request.getAccessToken(),
                request.getTarget(), request.getShopName(), status);
    }

    @Override
    public int updateStatusByShopNameAnd2(String shopName) {
        return baseMapper.updateStatusByShopNameAnd2(shopName);
    }

    @Override
    public Integer getIdByShopNameAndTargetAndSource(String shopName, String target, String source) {
        return baseMapper.getIdByShopNameAndTarget(shopName, target, source);
    }

    @Override
    public boolean updateStatus3To6(String shopName) {
        final int maxRetries = 3;
        int attempt = 0;

        while (attempt < maxRetries) {
            attempt++;
            try {
                int affectedRows = baseMapper.update(
                        new LambdaUpdateWrapper<TranslatesDO>()
                                .eq(TranslatesDO::getShopName, shopName)
                                .eq(TranslatesDO::getStatus, 3)
                                .set(TranslatesDO::getStatus, 6)
                );

                TraceReporterHolder.report("TranslatesServiceImpl.updateStatus3To6", "updateStatus3To6: " + shopName + " 修改行数：" + affectedRows);

                return true;
            } catch (Exception e) {
                ExceptionReporterHolder.report("TranslatesServiceImpl.updateStatus3To6", e);
                if (attempt >= maxRetries) {
                    TraceReporterHolder.report("TranslatesServiceImpl.updateStatus3To6", "FatalException updateStatus3To6: " + shopName + " 最终失败");
                }
            }
        }
        return false;
    }

    @Override
    public void updateAutoTranslateByShopNameToFalse(String shopName) {
        baseMapper.update(new UpdateWrapper<TranslatesDO>().eq("shop_name", shopName).set("auto_translate", false));
    }

    @Override
    public void insertLanguageStatus(TranslateRequest request) {
        Integer status = readStatus(request);
        if (status == null) {
            insertShopTranslateInfo(request, 0);
        }
    }

    @Override
    public void updateAllStatusTo0(String shopName) {
        if (shopName != null) {
            baseMapper.update(new UpdateWrapper<TranslatesDO>().eq("shop_name", shopName).set("status", 0));
        }
    }
}
