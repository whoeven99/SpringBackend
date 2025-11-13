package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IAPGOfficialTemplateService;
import com.bogdatech.entity.DO.APGOfficialTemplateDO;
import com.bogdatech.mapper.APGOfficialTemplateMapper;
import org.springframework.stereotype.Service;
import java.util.List;


@Service
public class APGOfficialTemplateServiceImpl extends ServiceImpl<APGOfficialTemplateMapper, APGOfficialTemplateDO> implements IAPGOfficialTemplateService {
    @Override
    public Boolean updateUsedTime(Long templateId) {
        return baseMapper.updateUsedTime(templateId) > 0;
    }

    @Override
    public List<APGOfficialTemplateDO> selectFirstFiveTemplateId() {
        QueryWrapper<APGOfficialTemplateDO> wrapper = new QueryWrapper<>();
        wrapper.select(
                "TOP 5 id"
        );

        // source 是参数
        wrapper.orderByAsc("create_time");

        return baseMapper.selectList(wrapper);
    }

    @Override
    public List<APGOfficialTemplateDO> selectOfficalTemplatesById(List<Long> listOfficeId) {
        return baseMapper.selectList(
                new LambdaQueryWrapper<APGOfficialTemplateDO>()
                        .in(APGOfficialTemplateDO::getId, listOfficeId)
                        .orderByDesc(APGOfficialTemplateDO::getUpdateTime));
    }

    @Override
    public APGOfficialTemplateDO getOfficialTemplateById(Long templateId) {
        return baseMapper.selectOne(new LambdaQueryWrapper<APGOfficialTemplateDO>().eq(APGOfficialTemplateDO::getId, templateId));
    }
}
