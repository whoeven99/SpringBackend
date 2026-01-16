package com.bogda.service.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.service.entity.DO.APGUserPlanDO;

public interface IAPGUserPlanService extends IService<APGUserPlanDO> {
    Boolean initializeFreePlan(Long userId);

    Integer getUserMaxLimit(Long id);
}
