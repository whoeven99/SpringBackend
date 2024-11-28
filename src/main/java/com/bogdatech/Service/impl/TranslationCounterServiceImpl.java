package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.entity.TranslationCounterDO;
import com.bogdatech.mapper.TranslationCounterMapper;
import com.bogdatech.model.controller.request.TranslationCounterRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TranslationCounterServiceImpl extends ServiceImpl<TranslationCounterMapper, TranslationCounterDO> implements ITranslationCounterService {


    @Override
    public TranslationCounterDO readCharsByShopName(TranslationCounterRequest request) {
        return baseMapper.readCharsByShopName(request.getShopName());
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
    public int getMaxCharsByShopName(String shopName) {
        return baseMapper.getMaxCharsByShopName(shopName);
    }
}
