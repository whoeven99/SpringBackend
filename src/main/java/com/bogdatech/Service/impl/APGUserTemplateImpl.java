package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IAPGUserTemplateService;
import com.bogdatech.entity.DO.APGUserTemplateDO;
import com.bogdatech.mapper.APGUserTemplateMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class APGUserTemplateImpl extends ServiceImpl<APGUserTemplateMapper, APGUserTemplateDO> implements IAPGUserTemplateService {
    @Override
    public List<APGUserTemplateDO> selectUserTemplatesById(List<Long> listUserId) {
        return baseMapper.selectList(
                new LambdaQueryWrapper<APGUserTemplateDO>()
                        .in(APGUserTemplateDO::getId, listUserId)
                        .orderByDesc(APGUserTemplateDO::getUpdateTime));
    }

    @Override
    public APGUserTemplateDO getUserTemplateByUserIdAndTemplateId(Long userId, Long templateId) {
        return baseMapper.selectOne(new LambdaQueryWrapper<APGUserTemplateDO>().eq(APGUserTemplateDO::getUserId, userId)
                .eq(APGUserTemplateDO::getId, templateId));
    }
}
