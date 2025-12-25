package com.bogda.common.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.common.service.IAPGUserTemplateService;
import com.bogda.common.entity.DO.APGUserTemplateDO;
import com.bogda.common.mapper.APGUserTemplateMapper;
import org.springframework.stereotype.Service;

@Service
public class APGUserTemplateImpl extends ServiceImpl<APGUserTemplateMapper, APGUserTemplateDO> implements IAPGUserTemplateService {
}
