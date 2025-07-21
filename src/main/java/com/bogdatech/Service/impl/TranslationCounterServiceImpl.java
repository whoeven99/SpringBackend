package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.entity.DO.TranslationCounterDO;
import com.bogdatech.mapper.TranslationCounterMapper;
import com.bogdatech.model.controller.request.TranslationCounterRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@Service
@Transactional
public class TranslationCounterServiceImpl extends ServiceImpl<TranslationCounterMapper, TranslationCounterDO> implements ITranslationCounterService {


    @Override
    public TranslationCounterDO readCharsByShopName(String shopName) {
        return baseMapper.readCharsByShopName(shopName);
    }

    @Override
    public int insertCharsByShopName(TranslationCounterRequest translationCounterRequest) {
        return baseMapper.insertCharsByShopName(translationCounterRequest.getShopName(), 0);
    }

    @Override
    public int updateUsedCharsByShopName(TranslationCounterRequest translationCounterRequest) {
        return baseMapper.updateUsedCharsByShopName(translationCounterRequest.getShopName(), translationCounterRequest.getUsedChars());
    }

    @Override
    public Integer getMaxCharsByShopName(String shopName) {
        return baseMapper.getMaxCharsByShopName(shopName);
    }

    @Override
    public Boolean updateCharsByShopName(TranslationCounterRequest request) {
        return baseMapper.updateCharsByShopName(request.getShopName(), request.getChars());
    }

    @Override
    public TranslationCounterDO getOneForUpdate(String shopName) {
        return baseMapper.getOneForUpdate(shopName);
    }

    @Override
    public Boolean updateAddUsedCharsByShopName(String shopName, Integer usedChars, Integer maxChars) {
        final int maxRetries = 3;
        final long retryDelayMillis = 500;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            try {
                Boolean b = baseMapper.updateAddUsedCharsByShopName(shopName, usedChars, maxChars);
                if (Boolean.TRUE.equals(b)) {
                    return true;
                } else {
                    retryCount++;
                    appInsights.trackTrace("更新失败（返回false） errors ，准备第" + retryCount + "次重试，shopName=" + shopName);
                }
            } catch (Exception e) {
                retryCount++;
                appInsights.trackTrace("更新失败（抛异常） errors ，准备第" + retryCount + "次重试，shopName=" + shopName + ", 错误=" + e);
            }

            try {
                Thread.sleep(retryDelayMillis);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        appInsights.trackTrace("更新失败 errors ，重试" + maxRetries + "次后仍未成功，shopName=" + shopName);
        return false;
    }


}
