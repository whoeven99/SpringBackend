package com.bogda.api.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.api.Service.IAPGUserPlanService;
import com.bogda.api.entity.DO.APGUserPlanDO;
import com.bogda.api.mapper.APGUserPlanMapper;
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
