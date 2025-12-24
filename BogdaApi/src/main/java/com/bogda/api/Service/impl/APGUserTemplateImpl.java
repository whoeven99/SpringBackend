package com.bogda.api.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.api.Service.IAPGUserTemplateService;
import com.bogda.api.entity.DO.APGUserTemplateDO;
import com.bogda.api.mapper.APGUserTemplateMapper;
import org.springframework.stereotype.Service;

@Service
public class APGUserTemplateImpl extends ServiceImpl<APGUserTemplateMapper, APGUserTemplateDO> implements IAPGUserTemplateService {
}
