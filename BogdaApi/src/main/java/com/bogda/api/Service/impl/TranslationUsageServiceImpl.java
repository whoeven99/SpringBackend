package com.bogda.api.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.api.Service.ITranslationUsageService;
import com.bogda.api.entity.DO.TranslationUsageDO;
import com.bogda.api.mapper.TranslationUsageMapper;
import org.springframework.stereotype.Service;

@Service
public class TranslationUsageServiceImpl extends ServiceImpl<TranslationUsageMapper, TranslationUsageDO> implements ITranslationUsageService {

}
