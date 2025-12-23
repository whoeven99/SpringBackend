package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.APGUserPlanDO;

public interface IAPGUserPlanService extends IService<APGUserPlanDO> {
    Boolean initializeFreePlan(Long userId);

    Integer getUserMaxLimit(Long id);
}
