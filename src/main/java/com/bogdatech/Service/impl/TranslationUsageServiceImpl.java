package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.ITranslationUsageService;
import com.bogdatech.entity.DO.TranslatesDO;
import com.bogdatech.entity.DO.TranslationUsageDO;
import com.bogdatech.mapper.TranslationUsageMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@Service
public class TranslationUsageServiceImpl extends ServiceImpl<TranslationUsageMapper, TranslationUsageDO> implements ITranslationUsageService {
    @Override
    public List<TranslationUsageDO> readTranslationUsageData(String shopName) {
        return baseMapper.selectList(new QueryWrapper<TranslationUsageDO>().eq("shop_name", shopName).eq("status", 1));
    }

    @Override
    public void insertOrUpdateSingleData(TranslationUsageDO translationUsageDO) {
        //没有就插入， 有就更新
        TranslationUsageDO translationUsageDO1 = baseMapper.selectOne(new QueryWrapper<TranslationUsageDO>().eq("translate_id", translationUsageDO.getTranslateId()).eq("language_name", translationUsageDO.getLanguageName()));
        if (translationUsageDO1 == null) {
            //获取用户该语言的id
            baseMapper.insert(translationUsageDO);
        }else {
            baseMapper.update(new UpdateWrapper<TranslationUsageDO>().eq("translate_id", translationUsageDO.getTranslateId())
                    .eq("language_name", translationUsageDO.getLanguageName())
                    .set("status", 1)
                    .set("credit_count", translationUsageDO.getCreditCount())
                    .set("consumed_time", translationUsageDO.getConsumedTime())
                    .set("remaining_credits", translationUsageDO.getRemainingCredits()));
        }
    }

    @Override
    public Boolean judgeSendAutoEmail(List<TranslatesDO> translatesDOList, String shopName) {
        //判断TranslationUsage里面的语言是否都翻译了，如果有就发送邮件；没有的话，就跳过
        List<TranslationUsageDO> translationUsageDOS = baseMapper.selectList(new QueryWrapper<TranslationUsageDO>().eq("shop_name", shopName).eq("status", 1));
        appInsights.trackTrace("emailAutoTranslate 用户： " + shopName + " 已翻译列表大小为： " + translationUsageDOS.size() + " 自动翻译列表： " + translationUsageDOS);
        //发送邮件
        return translationUsageDOS.size() == translatesDOList.size();
    }

    @Override
    public void insertListData(List<TranslatesDO> list, String shopName) {
        if (list == null || list.isEmpty()) {
            return;
        }
        //获取TranslationUsage表数据
        List<TranslationUsageDO> translationUsageDOS = baseMapper.selectList(new QueryWrapper<TranslationUsageDO>().eq("shop_name", shopName));
        // 构造已存在的记录集合，格式为 "id_lang"
        Set<String> existingKeys = translationUsageDOS.stream()
                .map(record -> record.getTranslateId() + "_" + record.getLanguageName())
                .collect(Collectors.toSet());

        // 过滤出需要插入的记录
        List<TranslationUsageDO> toInsert = list.stream()
                .filter(item -> !existingKeys.contains(item.getId() + "_" + item.getTarget()))
                .map(item -> new TranslationUsageDO(
                        item.getId(),
                        shopName,
                        item.getTarget(),
                        0, 0, 0, 0
                ))
                .toList();

        // 插入数据
        for (TranslationUsageDO usageDO : toInsert) {
            baseMapper.insert(usageDO);
        }
    }

    @Override
    public boolean updateUsageDataByShopName(String shopName, int status, int remainingCredits, int consumedTime, int creditCount) {
        return baseMapper.update(new LambdaUpdateWrapper<TranslationUsageDO>()
                .eq(TranslationUsageDO::getShopName, shopName)
                .set(TranslationUsageDO::getStatus, status)
                .set(TranslationUsageDO::getRemainingCredits, remainingCredits)
                .set(TranslationUsageDO::getConsumedTime, consumedTime)
                .set(TranslationUsageDO::getCreditCount, creditCount)) > 0;
    }

    @Override
    public boolean updateUsageToCompleteByShopNameAndTarget(String shopName, String target, long costTime, int usedChars, int endChars, int i) {
        return  baseMapper.update(new LambdaUpdateWrapper<TranslationUsageDO>()
                .eq(TranslationUsageDO::getShopName, shopName)
                .eq(TranslationUsageDO::getLanguageName, target)
                .set(TranslationUsageDO::getConsumedTime, costTime)
                .set(TranslationUsageDO::getCreditCount, usedChars)
                .set(TranslationUsageDO::getRemainingCredits, endChars)
                .set(TranslationUsageDO::getStatus, i)) > 0;
    }

}
