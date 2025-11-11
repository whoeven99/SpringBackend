package com.bogdatech.Service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IAPGUserTemplateMappingService;
import com.bogdatech.entity.DO.APGUserTemplateMappingDO;
import com.bogdatech.mapper.APGUserTemplateMappingMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class APGUserTemplateMappingServiceImpl extends ServiceImpl<APGUserTemplateMappingMapper, APGUserTemplateMappingDO> implements IAPGUserTemplateMappingService {
    @Override
    public List<APGUserTemplateMappingDO> selectMappingByIdNotDeleted(Long id, int isDeleted) {
        return baseMapper.selectList(new LambdaQueryWrapper<APGUserTemplateMappingDO>().eq(APGUserTemplateMappingDO::getUserId, id)
                .eq(APGUserTemplateMappingDO::getIsDelete, isDeleted).orderByDesc(APGUserTemplateMappingDO::getUpdateTime));
    }

    @Override
    public Boolean updateUserTemplateByUserIdAndTemplateId(APGUserTemplateMappingDO apgUserTemplateMappingDO, Long userId, Long templateId) {
        return baseMapper.update(apgUserTemplateMappingDO, new LambdaQueryWrapper<APGUserTemplateMappingDO>()
                .eq(APGUserTemplateMappingDO::getUserId, userId).eq(APGUserTemplateMappingDO::getTemplateId, templateId)) > 0;
    }
}
