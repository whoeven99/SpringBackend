package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.ITranslateTextService;
import com.bogdatech.entity.TranslateTextDO;
import com.bogdatech.mapper.TranslateTextMapper;
import com.bogdatech.model.controller.request.TranslateTextRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TranslateTextServiceImpl extends ServiceImpl<TranslateTextMapper, TranslateTextDO> implements ITranslateTextService {


    @Override
    public Integer insertTranslateText(TranslateTextDO request) {
        return baseMapper.insert(request);
    }

    @Override
    public TranslateTextDO getTranslateText(TranslateTextRequest request) {
        return baseMapper.getTranslateText(request.getShopName(), request.getResourceId(), request.getTextKey(),
                request.getSourceCode(), request.getTargetCode());
    }

    @Override
    public Integer updateTranslateText(TranslateTextRequest request) {
        return baseMapper.updateTranslateText(request.getTextKey(), request.getDigest(), request.getShopName(), request.getTargetCode());
    }

    @Override
    public TranslateTextDO getTranslateTextInfo(TranslateTextRequest request) {
        return baseMapper.getTranslateTextInfo(request.getDigest(), request.getShopName(), request.getTargetCode());
    }

    @Override
    public String getTargetTextByDigest(String digest) {
        return baseMapper.getTargetTextByDigest(digest);
    }


}
