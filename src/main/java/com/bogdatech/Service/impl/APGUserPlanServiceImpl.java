package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IAPGUserPlanService;
import com.bogdatech.entity.DO.APGUserPlanDO;
import com.bogdatech.mapper.APGUserPlanMapper;
import org.springframework.stereotype.Service;

@Service
public class APGUserPlanServiceImpl extends ServiceImpl<APGUserPlanMapper, APGUserPlanDO> implements IAPGUserPlanService {
    @Override
    public Boolean initializeFreePlan(Long userId) {
        return baseMapper.initializeFreePlan(userId);
    }

    @Override
    public Integer getUserMaxLimit(Long userId) {
        return baseMapper.getUserMaxLimit(userId);
    }
}
