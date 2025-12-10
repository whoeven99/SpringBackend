package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.ITranslationUsageService;
import com.bogdatech.entity.DO.TranslationUsageDO;
import com.bogdatech.mapper.TranslationUsageMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TranslationUsageServiceImpl extends ServiceImpl<TranslationUsageMapper, TranslationUsageDO> implements ITranslationUsageService {
    @Override
    public List<TranslationUsageDO> readTranslationUsageData(String shopName) {
        return baseMapper.selectList(new QueryWrapper<TranslationUsageDO>().eq("shop_name", shopName).eq("status", 1));
    }
}
