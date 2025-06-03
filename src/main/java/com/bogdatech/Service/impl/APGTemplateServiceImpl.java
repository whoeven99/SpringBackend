package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IAPGTemplateService;
import com.bogdatech.entity.DO.APGTemplateDO;
import com.bogdatech.mapper.APGTemplateMapper;
import org.springframework.stereotype.Service;

@Service
public class APGTemplateServiceImpl extends ServiceImpl<APGTemplateMapper, APGTemplateDO>  implements IAPGTemplateService {
    @Override
    public String getTemplateById(Long id) {
        return baseMapper.selectOne(new QueryWrapper<APGTemplateDO>().eq("id",id)).getTemplateData();
    }
}
