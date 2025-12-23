package com.bogda.common.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.common.Service.IAPGOfficialTemplateService;
import com.bogda.common.entity.DO.APGOfficialTemplateDO;
import com.bogda.common.mapper.APGOfficialTemplateMapper;
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
}
