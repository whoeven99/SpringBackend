package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.ITranslationUsageService;
import com.bogdatech.entity.DO.TranslationUsageDO;
import com.bogdatech.mapper.TranslationUsageMapper;
import org.springframework.stereotype.Service;

@Service
public class TranslationUsageServiceImpl extends ServiceImpl<TranslationUsageMapper, TranslationUsageDO> implements ITranslationUsageService {

}
