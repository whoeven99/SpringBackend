package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IAPGTemplateService;
import com.bogdatech.entity.DO.APGTemplateDO;
import com.bogdatech.mapper.APGTemplateMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class APGTemplateServiceImpl extends ServiceImpl<APGTemplateMapper, APGTemplateDO>  implements IAPGTemplateService {
    @Override
    public String getTemplateById(Long id) {
        return baseMapper.selectOne(new QueryWrapper<APGTemplateDO>().eq("id",id)).getTemplateData();
    }

    @Override
    public List<APGTemplateDO> getAllTemplateData(String shopName) {
        if ("system".equals(shopName)){
            return baseMapper.selectList(new QueryWrapper<APGTemplateDO>().eq("user_id", 0));
        }
        return baseMapper.getAllTemplateData(shopName);
    }
}
