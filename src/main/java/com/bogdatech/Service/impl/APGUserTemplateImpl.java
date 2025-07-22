package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IAPGUserTemplateService;
import com.bogdatech.entity.DO.APGUserTemplateDO;
import com.bogdatech.mapper.APGUserTemplateMapper;
import org.springframework.stereotype.Service;

@Service
public class APGUserTemplateImpl extends ServiceImpl<APGUserTemplateMapper, APGUserTemplateDO> implements IAPGUserTemplateService {
}
