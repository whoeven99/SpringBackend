package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.ITranslateTextService;
import com.bogdatech.entity.TranslateTextDO;
import com.bogdatech.mapper.TranslateTextMapper;
import com.bogdatech.model.controller.request.TranslateTextRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
        int batchSize = 300; // 每批处理的数据量
        for (int i = 0; i < translateTexts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, translateTexts.size());
            List<TranslateTextDO> batch = translateTexts.subList(i, end);

            LambdaQueryWrapper<TranslateTextDO> queryWrapper = new LambdaQueryWrapper<>();
            Set<String> uniqueKeys = batch.stream()
                    .map(translateText ->
                            translateText.getShopName() + "|" +
                                    translateText.getResourceId() + "|" +
                                    translateText.getTargetCode() + "|" +
                                    translateText.getSourceText() + "|" +
                                    translateText.getTargetText())
                    .collect(Collectors.toSet());

            List<String> uniqueKeyList = new ArrayList<>(uniqueKeys);

            queryWrapper.in(TranslateTextDO::getShopName, uniqueKeyList.stream().map(key -> key.split("\\|")[0]).collect(Collectors.toList()))
                    .in(TranslateTextDO::getResourceId, uniqueKeyList.stream().map(key -> key.split("\\|")[1]).collect(Collectors.toList()))
                    .in(TranslateTextDO::getTargetCode, uniqueKeyList.stream().map(key -> key.split("\\|")[2]).collect(Collectors.toList()))
                    .in(TranslateTextDO::getSourceText, uniqueKeyList.stream().map(key -> key.split("\\|")[3]).collect(Collectors.toList()))
                    .in(TranslateTextDO::getTargetText, uniqueKeyList.stream().map(key -> key.split("\\|")[4]).collect(Collectors.toList()));

            List<TranslateTextDO> existingEntities = this.list(queryWrapper);

            // 过滤出当前批次中不存在的新记录
            List<TranslateTextDO> newEntitiesInBatch = batch.stream()
                    .filter(translateText -> !existingEntities.contains(translateText))
                    .collect(Collectors.toList());

            if (!newEntitiesInBatch.isEmpty()) {
                System.out.println("newEntitiesInBatch:" + newEntitiesInBatch.stream().toList());
                this.saveBatch(newEntitiesInBatch);
            }
        }
    }


}
