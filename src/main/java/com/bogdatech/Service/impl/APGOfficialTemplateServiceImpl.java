package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IAPGOfficialTemplateService;
import com.bogdatech.entity.DO.APGOfficialTemplateDO;
import com.bogdatech.mapper.APGOfficialTemplateMapper;
import org.springframework.stereotype.Service;

@Service
public class APGOfficialTemplateServiceImpl extends ServiceImpl<APGOfficialTemplateMapper, APGOfficialTemplateDO> implements IAPGOfficialTemplateService {
}
