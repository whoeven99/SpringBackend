package com.bogda.common.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.common.Service.ITranslationUsageService;
import com.bogda.common.entity.DO.TranslationUsageDO;
import com.bogda.common.mapper.TranslationUsageMapper;
import org.springframework.stereotype.Service;

@Service
public class TranslationUsageServiceImpl extends ServiceImpl<TranslationUsageMapper, TranslationUsageDO> implements ITranslationUsageService {

}
