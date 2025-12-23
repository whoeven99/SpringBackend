package com.bogda.common.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.common.Service.IAPGUserPlanService;
import com.bogda.common.entity.DO.APGUserPlanDO;
import com.bogda.common.mapper.APGUserPlanMapper;
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
