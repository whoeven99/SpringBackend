package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.entity.DO.TranslationCounterDO;
import com.bogdatech.mapper.TranslationCounterMapper;
import com.bogdatech.model.controller.request.TranslationCounterRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        //TODO:做重试机制
        return baseMapper.updateAddUsedCharsByShopName(shopName, usedChars, maxChars);
    }


}
