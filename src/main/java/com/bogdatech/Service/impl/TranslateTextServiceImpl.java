package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.ITranslateTextService;
import com.bogdatech.entity.TranslateTextDO;
import com.bogdatech.mapper.TranslateTextMapper;
import com.bogdatech.model.controller.request.TranslateTextRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class TranslateTextServiceImpl extends ServiceImpl<TranslateTextMapper, TranslateTextDO> implements ITranslateTextService {


    @Override
    public Integer insertTranslateText(TranslateTextDO request) {
        return baseMapper.insert(request);
    }

    @Override
    public TranslateTextDO getTranslateText(TranslateTextDO request) {
        return baseMapper.getTranslateText(request.getShopName(), request.getResourceId(), request.getTextKey(),
                request.getSourceCode(), request.getTargetCode());
    }

    @Override
    public Integer updateTranslateText(TranslateTextRequest request) {
        return baseMapper.updateTranslateText(request.getTargetText(), request.getDigest(), request.getShopName(), request.getTargetCode());
    }

    @Override
    public TranslateTextDO getTranslateTextInfo(TranslateTextRequest request) {
        return baseMapper.getTranslateTextInfo(request.getDigest(), request.getShopName(), request.getTargetCode());
    }

    @Override
    public String[] getTargetTextByDigest(String digest, String target) {
        return baseMapper.getTargetTextByDigest(digest, target);
    }

    @Override
    public void getExistTranslateTextList(List<TranslateTextDO> translateTexts) {
        // 构建查询条件
        LambdaQueryWrapper<TranslateTextDO> queryWrapper = new LambdaQueryWrapper<>();

        // 添加查询条件
        for (TranslateTextDO translateText : translateTexts) {
            queryWrapper.or(w -> w.eq(TranslateTextDO::getShopName, translateText.getShopName())
                    .eq(TranslateTextDO::getResourceId, translateText.getResourceId())
                    .eq(TranslateTextDO::getTargetCode, translateText.getTargetCode())
                    .eq(TranslateTextDO::getSourceText, translateText.getSourceText())
                    .eq(TranslateTextDO::getTargetText, translateText.getTargetText())
                    .eq(TranslateTextDO::getShopName, translateText.getShopName()));
        }

        // 查询数据库中已存在的记录
        List<TranslateTextDO> existingEntities = this.list(queryWrapper);
//        System.out.println("existingEntities: " + existingEntities);
        // 过滤出数据库中不存在的新记录
        List<TranslateTextDO> newEntities = translateTexts.stream()
                .filter(translateText -> !existingEntities.contains(translateText))
                .collect(Collectors.toList());
        System.out.println("newEntities: " + newEntities);
        // 批量插入新记录
        if (!newEntities.isEmpty()) {
            this.saveBatch(newEntities);
        }

    }


}
